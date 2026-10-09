package org.mesutormanli.bistadvisor.advisor;

import org.mesutormanli.bistadvisor.config.AdvisorMode;
import org.mesutormanli.bistadvisor.config.ModelType;
import org.mesutormanli.bistadvisor.model.Labeler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Günlük AL/TUT/SAT önerilerini üreten saf (durumsuz) planlayıcı.
 * <p>
 * Veri erişimi ve model tahmini dışarıdan hazır girdi olarak verilir; bu sınıf
 * portföy durumunu <em>değiştirmez</em>, yalnızca öneri üretir. Böylece karar
 * kuralları ağ erişimi olmadan test edilebilir. Öne çıkan davranışları:
 * </p>
 * <ul>
 *   <li>Stop-loss (%10/%15/%25 risk moduna göre) ya da modelin SAT sınıfı +
 *       satış skor eşiğini geçen güven ile SAT'a işaretlenen sembol, aynı turda
 *       AL listesine girmez.</li>
 *   <li>Pozisyon slotu yalnızca <em>yeni</em> pozisyon adayları için harcanır;
 *       mevcut pozisyona ekleme slot harcamaz.</li>
 *   <li>Satışların tahmini brüt geliri (lot × fiyat) planlanmış sermayeye eklenir.</li>
 *   <li>Fiyat verisi olmayan pozisyon için sahte fiyat üretilmez; TUT önerilir
 *       ve uyarı listesine not düşülür.</li>
 *   <li>Alım bütçesi seçilen adayların skorlarına orantılı dağıtılır.</li>
 * </ul>
 *
 * @see ScoreGate
 */
public final class AllocationPlanner {

    /**
     * Mevcut bir pozisyonun karar öncesi görünümü.
     *
     * @param symbol    pozisyondaki hissenin sembolü
     * @param lots      pozisyondaki lot adedi
     * @param avgCost   pozisyonun ortalama maliyeti
     * @param price     güncel fiyat; {@code null} olması fiyat verisinin alınamadığını belirtir
     * @param predClass model sınıf tahmini ({@link Labeler#BUY} vb.);
     *                  {@code null} olması tahmin üretilemediğini belirtir
     * @param score     model skoru/güveni; {@code null} olması tahmin üretilemediğini belirtir
     */
    public record HoldingInput(String symbol, int lots, double avgCost,
                               Double price, Integer predClass, Double score) {}

    /**
     * Alım adayının karar öncesi görünümü.
     *
     * @param symbol    aday hissenin sembolü
     * @param price     adayın güncel fiyatı
     * @param score     model skoru/güveni
     * @param predClass model sınıf tahmini ({@link Labeler#BUY} vb.)
     * @param existing  adayın mevcut bir pozisyona ek mi olduğu; {@code true} ise
     *                  slot harcamadan seçime dâhil edilir
     */
    public record CandidateInput(String symbol, double price, Double score,
                                 Integer predClass, boolean existing) {}

    /**
     * Tek bir sembol için üretilen AL/TUT/SAT önerisi.
     *
     * @param symbol önerinin ait olduğu hissenin sembolü
     * @param action işlem: {@code AL}, {@code TUT} ya da {@code SAT}
     * @param lots   önerilen lot adedi (mevcut pozisyonlarda mevcut lot)
     * @param price  kararın dayanak fiyatı; {@code null} = fiyat verisi yok
     * @param score  model skoru/güveni; {@code null} = tahmin yok
     * @param note   kararın gerekçesini özetleyen not (örn. k/z yüzdesi, skor)
     */
    public record Planned(String symbol, String action, int lots,
                          Double price, Double score, String note) {}

    /**
     * Günlük planın tamamı: pozisyon önerileri, alım önerileri ve plan özeti.
     *
     * @param holdings        mevcut pozisyonlar için öneriler (aynı sırada)
     * @param buys            alım önerileri (skora göre azalan sırada)
     * @param buySlots        boş kalan yeni pozisyon slotu sayısı
     * @param plannedCapital  planlanmış sermaye (nakit + satış geliri)
     * @param warnings        kullanıcıya gösterilecek uyarı satırları
     */
    public record Plan(List<Planned> holdings, List<Planned> buys, int buySlots,
                       double plannedCapital, List<String> warnings) {}

    /**
     * Örnek oluşturulmasını engelleyen gizli yapıcı; sınıf yalnızca statik
     * {@link #plan} yöntemiyle kullanılır.
     */
    private AllocationPlanner() {}

    /**
     * Pozisyonlar ve adaylar için günlük AL/TUT/SAT planını üretir.
     *
     * <p>Akış şöyledir:</p>
     * <ol>
     *   <li>Her pozisyon için k/z yüzdesi (fiyat − ortalama maliyet) / ortalama
     *       maliyet hesaplanır ve not metnine "{@code +/-X.XX%}" biçiminde yazılır.
     *       Fiyat yoksa "veri yok" notu ile TUT önerilir ve uyarı eklenir.
     *       SAT kararı iki durumdan biriyle verilir: stop-loss (k/z ≤ −stopLossPct)
     *       ya da modelin SAT sınıfı + {@link ScoreGate#passes} ile
     *       {@code sellScoreThreshold} eşiğini geçen güven. SAT notuna skor eklenir,
     *       sembol satılacak listeye alınır ve brüt satış geliri (lot × fiyat)
     *       biriktirilir.</li>
     *   <li>Slot hesabı: satıştan boşalan yerler yeni pozisyonlara ayrılır.</li>
     *   <li>Aday eleme: SAT'a giren sembol asla AL listesine girmez; adayın
     *       sınıfı {@link Labeler#BUY} olmalı ve skoru alım eşiğini
     *       {@link ScoreGate#passes} ile geçmelidir.</li>
     *   <li>Sıralama ve seçim: adaylar skora göre azalan sıralanır. Mevcut
     *       pozisyona ekleme ({@code existing=true}) slot harcamaz ve her zaman
     *       seçilir; yeni adaylar boş slot sayısıyla sınırlıdır.</li>
     *   <li>Sermaye dağıtım: planlanmış sermaye = nakit + satış geliri; toplam
     *       bütçe = planlanmış sermaye × risk yüzdesi. Bütçe, skorlarla orantılı
     *       dağıtılır (pay = bütçe × skor / toplam skor); ayrılan paydan
     *       {@code floor(pay / fiyat)} lot çıkar, 0 lot çıkan aday atlanır.</li>
     * </ol>
     *
     * @param holdings    karar verilecek mevcut pozisyonlar
     * @param candidates  alım adayları (portföyde olmayan endeks hisseleri ve
     *                    mevcut pozisyonlara ekleme adayları)
     * @param mode        risk profili; risk yüzdesi, alım eşiği, stop-loss ve
     *                    satış skor eşiğini belirler
     * @param modelType   eşik yorumlamasında kullanılan model türü
     *                    ({@link ScoreGate#effectiveThreshold})
     * @param cash        portföyün kullanılabilir nakdi
     * @param maxPositions izin verilen maksimum pozisyon sayısı
     * @return pozisyon önerileri, alım önerileri, boş slot, planlanmış sermaye ve
     *         uyarıları içeren {@link Plan}; dönen listeler değiştirilemezdir
     * @implNote Yöntem salt okunurdur: girdi listelerine ve portföy durumuna yazma
     *           yapmaz, durum tutmaz.
     */
    public static Plan plan(List<HoldingInput> holdings, List<CandidateInput> candidates,
                            AdvisorMode mode, ModelType modelType,
                            double cash, int maxPositions) {
        List<String> warnings = new ArrayList<>();
        List<Planned> holdingPlans = new ArrayList<>();
        Set<String> sellSymbols = new HashSet<>();
        double sellProceeds = 0.0;

        for (HoldingInput h : holdings) {
            if (h.price() == null) {
                warnings.add("Fiyat verisi alinamadi: " + h.symbol());
                holdingPlans.add(new Planned(h.symbol(), "TUT", h.lots(), null, null, "veri yok"));
                continue;
            }
            double price = h.price();
            double pnlPct = h.avgCost() > 0 ? (price - h.avgCost()) / h.avgCost() : 0.0;
            String note = (pnlPct >= 0 ? "+" : "") + String.format("%.2f", pnlPct * 100) + "%";
            boolean modelSell = h.predClass() != null && h.predClass() == Labeler.SELL
                    && h.score() != null
                    && ScoreGate.passes(modelType, h.score(), mode.sellScoreThreshold);
            boolean stopLoss = pnlPct <= -mode.stopLossPct;
            String action = (stopLoss || modelSell) ? "SAT" : "TUT";
            if ("SAT".equals(action)) {
                note += " | skor=" + String.format("%.2f", h.score() != null ? h.score() : 0.0);
                sellSymbols.add(h.symbol());
                sellProceeds += h.lots() * price;
            }
            holdingPlans.add(new Planned(h.symbol(), action, h.lots(), price, h.score(), note));
        }

        int sellCount = sellSymbols.size();
        int posCount = holdings.size();
        int buySlots = Math.clamp(maxPositions - (posCount - sellCount), 0, maxPositions);

        List<CandidateInput> eligible = new ArrayList<>();
        for (CandidateInput c : candidates) {
            if (sellSymbols.contains(c.symbol())) continue;
            if (c.predClass() == null || c.predClass() != Labeler.BUY) continue;
            if (c.score() == null || !ScoreGate.passes(modelType, c.score(), mode.buyThreshold)) continue;
            eligible.add(c);
        }

        eligible.sort(Comparator.comparingDouble((CandidateInput c) -> c.score()).reversed());
        List<CandidateInput> chosen = new ArrayList<>();
        int newSlotsUsed = 0;
        for (CandidateInput c : eligible) {
            if (c.existing()) {
                chosen.add(c);
            } else if (newSlotsUsed < buySlots) {
                chosen.add(c);
                newSlotsUsed++;
            }
        }
        chosen.sort(Comparator.comparingDouble((CandidateInput c) -> c.score()).reversed());

        double plannedCapital = Math.max(0.0, cash) + sellProceeds;
        double totalBudget = plannedCapital * mode.riskPct;
        double totalScore = chosen.stream().mapToDouble(CandidateInput::score).sum();

        List<Planned> buys = new ArrayList<>();
        if (totalScore > 0) {
            for (CandidateInput c : chosen) {
                double alloc = totalBudget * (c.score() / totalScore);
                int lots = (int) Math.floor(alloc / c.price());
                if (lots > 0) {
                    buys.add(new Planned(c.symbol(), "AL", lots, c.price(), c.score(),
                            "skor=" + String.format("%.2f", c.score())));
                }
            }
        }

        return new Plan(List.copyOf(holdingPlans), List.copyOf(buys),
                buySlots, plannedCapital, List.copyOf(warnings));
    }
}