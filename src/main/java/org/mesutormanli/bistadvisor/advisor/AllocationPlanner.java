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
 * Günlük öneri kararlarını üreten saf (side-effect'siz) planlayıcı.
 * <p>
 * Veri erişimi ve model tahmini {@code DailyAdvisor} tarafından hazırlanıp bu sınıfa
 * girdi olarak verilir; burada yalnızca karar mantığı çalışır — böylece karar kuralları
 * ağ erişimi olmadan birim teste alınabilir. Düzelttiği iç tutarsızlıklar:
 * <ul>
 *   <li>Stop-loss ile SAT işaretlenen sembol aynı turda AL adayı olamaz.</li>
 *   <li>Pozisyon slotu yalnızca <em>yeni</em> pozisyon adayları için sayılır;
 *       mevcut pozisyona ekleme slot harcamaz.</li>
 *   <li>Aynı turda önerilen satışların serbest bırakacağı sermaye (tahmini
 *       tutar = lot × güncel fiyat) alım bütçesine eklenir.</li>
 *   <li>Fiyat verisi olmayan pozisyonlar için sahte fiyat üretilmez; karar
 *       üretilmez ve uyarı listesine eklenir.</li>
 *   <li>Holding önerileri de gerçek skorunu taşır; eşikler model türüne göre
 *       yorumlanır ({@link ScoreGate}).</li>
 * </ul>
 */
public final class AllocationPlanner {

    /**
     * Mevcut pozisyonun karar öncesi görünümü.
     *
     * @param symbol    hisse sembolü
     * @param lots      lot sayısı
     * @param avgCost   ortalama maliyet
     * @param price     güncel fiyat ({@code null} = veri yok)
     * @param predClass model sınıf tahmini ({@link Labeler#BUY}/{@link Labeler#SELL}/
     *                  {@link Labeler#HOLD}; {@code null} = tahmin yok)
     * @param score     model güven skoru ({@code null} = tahmin yok)
     */
    public record HoldingInput(String symbol, int lots, double avgCost,
                               Double price, Integer predClass, Double score) {}

    /**
     * Alım adayının karar öncesi görünümü.
     *
     * @param symbol    hisse sembolü
     * @param price     güncel fiyat
     * @param score     model güven skoru
     * @param predClass model sınıf tahmini
     * @param existing  sembol portföyde mevcut bir pozisyon mu? (slot harcamaz)
     */
    public record CandidateInput(String symbol, double price, Double score,
                                 Integer predClass, boolean existing) {}

    /**
     * Sonuç önerisi.
     *
     * @param symbol hisse sembolü
     * @param action işlem (AL/SAT/TUT)
     * @param lots   lot miktarı
     * @param price  öneri fiyatı ({@code null} = veri yok)
     * @param score  model skoru ({@code null} = tahmin yok)
     * @param note   açıklama
     */
    public record Planned(String symbol, String action, int lots,
                          Double price, Double score, String note) {}

    /**
     * Planlanmış günlük kararlar.
     *
     * @param holdings        mevcut pozisyon önerileri (SAT/TUT)
     * @param buys            alım önerileri (AL)
     * @param buySlots        doldurulabilecek <em>yeni</em> pozisyon sayısı
     * @param plannedCapital  alım planında kullanılan sermaye (nakit + satışların
     *                        tahmini tutarı)
     * @param warnings        kullanıcıya gösterilecek uyarılar (ör. veri yok)
     */
    public record Plan(List<Planned> holdings, List<Planned> buys, int buySlots,
                       double plannedCapital, List<String> warnings) {}

    private AllocationPlanner() {}

    /**
     * Günlük planı üretir.
     *
     * @param holdings      mevcut pozisyonlar
     * @param candidates    alım adayları (filtrelenmemiş; planlayıcı eleyecektir)
     * @param mode          yatırım modu (eşikler/stop-loss/risk yüzdesi)
     * @param modelType     model türü (eşik yorumlaması için)
     * @param cash          kullanılabilir nakit
     * @param maxPositions  maksimum pozisyon sayısı
     * @return planlanmış kararlar
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

        // Aday eleme: SAT işaretli sembol AL'a girmez; fiyat/skor eşiği filtresi.
        List<CandidateInput> eligible = new ArrayList<>();
        for (CandidateInput c : candidates) {
            if (sellSymbols.contains(c.symbol())) continue;
            if (c.predClass() == null || c.predClass() != Labeler.BUY) continue;
            if (c.score() == null || !ScoreGate.passes(modelType, c.score(), mode.buyThreshold)) continue;
            eligible.add(c);
        }

        // Slot muhasebesi: slot yalnızca YENİ pozisyonlar için sayılır.
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

        // Sermaye planı: nakit + satışların tahmini tutarı.
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