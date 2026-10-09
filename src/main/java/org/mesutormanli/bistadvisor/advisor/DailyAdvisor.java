package org.mesutormanli.bistadvisor.advisor;

import org.mesutormanli.bistadvisor.advisor.AllocationPlanner.CandidateInput;
import org.mesutormanli.bistadvisor.advisor.AllocationPlanner.HoldingInput;
import org.mesutormanli.bistadvisor.advisor.AllocationPlanner.Plan;
import org.mesutormanli.bistadvisor.advisor.AllocationPlanner.Planned;
import org.mesutormanli.bistadvisor.config.AdvisorMode;
import org.mesutormanli.bistadvisor.config.AnalysisType;
import org.mesutormanli.bistadvisor.config.ModelType;
import org.mesutormanli.bistadvisor.data.BistIndices;
import org.mesutormanli.bistadvisor.data.CacheStore;
import org.mesutormanli.bistadvisor.data.YahooClient;
import org.mesutormanli.bistadvisor.data.YahooClient.Fundamentals;
import org.mesutormanli.bistadvisor.features.FeatureVector;
import org.mesutormanli.bistadvisor.features.TechnicalFeatures;
import org.mesutormanli.bistadvisor.features.TechnicalFeatures.Bar;
import org.mesutormanli.bistadvisor.model.ModelStrategy;
import org.mesutormanli.bistadvisor.model.ModelTrainer;
import org.mesutormanli.bistadvisor.portfolio.PortfolioService;
import org.mesutormanli.bistadvisor.portfolio.PortfolioState;
import org.mesutormanli.bistadvisor.portfolio.Position;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Günlük öneri akışının orkestratörü: veri yükleme, model tahmini ve planlama
 * adımlarını birleştirip kullanıcıya dönük {@link AnalysisResult} üretir.
 * <p>
 * Veri + model + planlama hat zinciri şu şekilde kurulur: {@link PortfolioService}
 * üzerinden portföy durumu (derin kopya) ve mod/model tipi okunur,
 * {@link ModelTrainer#getOrTrain} ile seçili endeks için model hazırlanır, sembol
 * bazlı fiyat serileri ve öznitelik vektörleri çalışma içi önbellekle tek sefer
 * yüklenir, karar mantığı {@link AllocationPlanner#plan} ile çalıştırılır ve sonuç
 * numaralı {@link Recommendation} listelerine çevrilir. Aynı sembol için bir
 * çalışma turunda tekrar veri kaynağına çıkılmaz; fiyat verisi alınamayan sembole
 * sahte fiyat üretilmez.
 * </p>
 */
@Service
public class DailyAdvisor {
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(DailyAdvisor.class);

    private final BistIndices bistIndices;
    private final YahooClient yahoo;
    private final CacheStore cacheStore;
    private final ModelTrainer modelTrainer;
    private final PortfolioService portfolioService;

    /**
     * Servisin bağımlılıklarını enjekte ederek yeni bir {@code DailyAdvisor} kurar.
     *
     * @param bistIndices     seçili endeksteki sembol listelerini sağlayan bileşen
     * @param yahoo           fiyat serisi ve temel verileri çeken istemci
     * @param cacheStore      yerel CSV fiyat önbelleği
     * @param modelTrainer    modelleri eğiten ya da önbellekten getiren servis
     * @param portfolioService portföy durumunu ve nakit/slot ayarlarını yöneten servis
     */
    public DailyAdvisor(BistIndices bistIndices, YahooClient yahoo,
                        CacheStore cacheStore,
                        ModelTrainer modelTrainer, PortfolioService portfolioService) {
        this.bistIndices = bistIndices;
        this.yahoo = yahoo;
        this.cacheStore = cacheStore;
        this.modelTrainer = modelTrainer;
        this.portfolioService = portfolioService;
    }

    /**
     * Numaralandırılmış tek bir AL/TUT/SAT önerisi.
     *
     * @param index  önerinin listedeki sıra numarası
     * @param symbol önerilen hissenin sembolü
     * @param action işlem: {@code AL}, {@code TUT} ya da {@code SAT}
     * @param lots   önerilen lot adedi
     * @param price  kararın dayanak fiyatı; {@code null} = fiyat verisi yok
     * @param score  model skoru/güveni; {@code null} = tahmin yok
     * @param note   kararın gerekçesini özetleyen not
     */
    public record Recommendation(int index, String symbol, String action,
                                 int lots, Double price, Double score, String note) {}

    /**
     * Bir analiz turunun tüm sonuçlarını kapsüler.
     *
     * @param holdings      mevcut pozisyonlar için AL/TUT/SAT önerileri
     * @param buys          alım (AL) önerileri
     * @param availableCash portföyün kullanılabilir nakdi
     * @param positionCount mevcut pozisyon sayısı
     * @param maxPositions  izin verilen maksimum pozisyon sayısı (5)
     * @param buySlots      doldurulabilecek boş alım slotu sayısı
     * @param warnings      kullanıcıya gösterilecek uyarı satırları
     */
    public record AnalysisResult(List<Recommendation> holdings, List<Recommendation> buys,
                                 double availableCash, int positionCount,
                                 int maxPositions, int buySlots, List<String> warnings) {}

    /**
     * Günlük analizi uçtan uca çalıştırır ve öneri listelerini üretir.
     *
     * <p>Portföy durumu, mod, model tipi ve analiz tipi okunup seçili endeks için model
     * hazırlanır; her pozisyon ve aday için fiyat serisi ile öznitelik vektörü
     * çalışma içi önbellekten (gerekirse yeniden çekilerek) sağlanır. Öznitelik
     * vektörleri seçilen {@link AnalysisType}'ın metrik kümesiyle üretilir ve model
     * de aynı kümeyle eğitildiği için boyutlar her zaman uyuşur. Temel gösterge
     * içeren analiz tiplerinde ({@link AnalysisType#usesFundamental()}) kullanıcıyı
     * uyaran bir not uyarı listesine eklenir. Pozisyonlar
     * {@link HoldingInput}, portföyde olmayan endeks hisseleri ve mevcut pozisyonlar
     * ({@code existing=true} ile eklemeye aday) {@link CandidateInput} olarak
     * {@link AllocationPlanner#plan} yöntemine verilir. Çıkan plan numaralı
     * önerilere çevrilir ve son çalıştırma tarihi portföy durumuna yazılır
     * ({@code lastRunDate} = bugün).</p>
     *
     * @return pozisyon önerileri, alım önerileri, nakit, pozisyon/slot sayıları ve
     *         uyarıları içeren analiz sonucu
     * @implNote Analiz bir kez çalıştırıldığında aynı sembol için hem bar serisi
     *           hem de öznitelik vektörü {@link #loadSeriesCached} ve
     *           {@link #featuresCached} ile memoize edilir.
     */
    public AnalysisResult analyze() {
        PortfolioState state = portfolioService.getState();
        AdvisorMode mode = portfolioService.advisorMode();
        ModelType modelType = portfolioService.modelType();
        AnalysisType analysisType = portfolioService.analysisType();
        ModelStrategy model = modelTrainer.getOrTrain(modelType, analysisType, state.selectedIndex);

        Map<String, List<Bar>> barsCache = new HashMap<>();
        Map<String, double[]> featureCache = new HashMap<>();
        Map<String, Double> currentPrices = new LinkedHashMap<>();

        List<HoldingInput> holdingInputs = new ArrayList<>();
        for (Position p : state.positions) {
            List<Bar> bars = loadSeriesCached(p.symbol(), barsCache);
            Double price = currentPrice(bars);
            Double score = null;
            Integer predClass = null;
            if (price != null) {
                currentPrices.put(p.symbol(), price);
                double[] pred = model.predict(featuresCached(p.symbol(), bars, analysisType, featureCache));
                predClass = (int) pred[0];
                score = pred[1];
            }
            holdingInputs.add(new HoldingInput(p.symbol(), p.lots(), p.avgCost(),
                    price, predClass, score));
        }

        Set<String> held = new HashSet<>();
        for (Position p : state.positions) held.add(p.symbol());

        List<CandidateInput> candidateInputs = new ArrayList<>();
        for (String sym : bistIndices.symbolsOf(state.selectedIndex)) {
            if (held.contains(sym)) continue;
            List<Bar> bars = loadSeriesCached(sym, barsCache);
            Double price = currentPrice(bars);
            if (price == null) continue;
            double[] pred = model.predict(featuresCached(sym, bars, analysisType, featureCache));
            candidateInputs.add(new CandidateInput(sym, price, pred[1], (int) pred[0], false));
        }
        for (Position p : state.positions) {
            List<Bar> bars = loadSeriesCached(p.symbol(), barsCache);
            Double price = currentPrice(bars);
            if (price == null) continue;
            double[] pred = model.predict(featuresCached(p.symbol(), bars, analysisType, featureCache));
            candidateInputs.add(new CandidateInput(p.symbol(), price, pred[1], (int) pred[0], true));
        }

        Plan plan = AllocationPlanner.plan(holdingInputs, candidateInputs,
                mode, modelType, portfolioService.availableCash(), portfolioService.maxPositions());

        int idx = 1;
        List<Recommendation> holdings = new ArrayList<>();
        for (Planned h : plan.holdings()) {
            holdings.add(new Recommendation(idx++, h.symbol(), h.action(), h.lots(),
                    h.price(), h.score(), h.note()));
        }
        List<Recommendation> buys = new ArrayList<>();
        for (Planned b : plan.buys()) {
            buys.add(new Recommendation(idx++, b.symbol(), b.action(), b.lots(),
                    b.price(), b.score(), b.note()));
        }

        List<String> warnings = new ArrayList<>();
        if (analysisType.usesFundamental()) {
            warnings.add("Analiz tipi " + analysisType.label
                    + ": temel göstergeler güncel tarihli olduğu için geçmişe dönük çalışmalarda yanıltıcı olabilir");
        }
        warnings.addAll(plan.warnings());

        portfolioService.updateState(s -> s.lastRunDate = LocalDate.now().toString());
        return new AnalysisResult(holdings, buys, portfolioService.availableCash(),
                state.positions.size(), portfolioService.maxPositions(),
                plan.buySlots(), List.copyOf(warnings));
    }

    /**
     * Mevcut pozisyonların güncel fiyatlarını toplar.
     *
     * @return sembol → son kapanış fiyatı eşlemesi; serisi/verisi olmayan semboller
     *         atlanır ve pozisyonların portföydeki sırası korunur
     * @implNote Sonuç {@link LinkedHashMap} ile döndüğü için ekleme (pozisyon)
     *           sırası korunur.
     */
    public Map<String, Double> currentPrices() {
        Map<String, Double> prices = new LinkedHashMap<>();
        for (Position p : portfolioService.getState().positions) {
            Double price = currentPrice(loadSeries(p.symbol()));
            if (price != null) prices.put(p.symbol(), price);
        }
        return prices;
    }

    /**
     * Sembolün fiyat serisini çalışma içi önbellekten döndürür, yoksa yükler.
     *
     * @param symbol yüklenecek hissenin sembolü
     * @param cache  çalışma süresince geçerli olan sembol → bar listesi memoizasyon haritası
     * @return sembolün bar serisi
     * @implNote Aynı sembol için bir çalışma turunda ikinci kez veri kaynağına
     *           çıkılmaz.
     */
    private List<Bar> loadSeriesCached(String symbol, Map<String, List<Bar>> cache) {
        return cache.computeIfAbsent(symbol, this::loadSeries);
    }

    /**
     * Sembolün fiyat serisini yerel CSV önbelleğinden okur, gerekirse tazeler.
     *
     * @param symbol yüklenecek hissenin sembolü
     * @return sembolün bar serisi
     * @implNote Önbellek taze değilse Yahoo'dan çekilen seri
     *           ({@code tarih,kapanış,hacim} satırları) CSV olarak yazılır; seri
     *           her durumda {@link TechnicalFeatures#toBars} ile CSV satırlarından
     *           okunur.
     */
    private List<Bar> loadSeries(String symbol) {
        if (!cacheStore.hasFresh(symbol)) {
            List<Bar> fetched = yahoo.fetchPrices(symbol);
            if (!fetched.isEmpty()) {
                cacheStore.writeLines(symbol, fetched.stream()
                        .map(b -> b.date() + "," + b.close() + "," + (long) b.volume()).toList());
            }
        }
        return TechnicalFeatures.toBars(cacheStore.readLines(symbol));
    }

    /**
     * Bar serisinin son kapanış fiyatını (güncel fiyat) döndürür.
     *
     * @param series kapanış fiyatları içeren bar serisi
     * @return serinin son kapanışı; seri boşsa fiyat bilinmediğinden {@code null}
     */
    private Double currentPrice(List<Bar> series) {
        return series.isEmpty() ? null : series.getLast().close();
    }

    /**
     * Sembolün normalize edilmiş öznitelik vektörünü çalışma içi önbellekten döndürür,
     * yoksa hesaplar.
     *
     * @param symbol       öznitelikleri hesaplanacak hissenin sembolü
     * @param bars         sembolün bar serisi
     * @param analysisType vektörün metrik kümesi (bkz. {@link FeatureVector#toArray(AnalysisType)})
     * @param featureCache çalışma süresince geçerli olan sembol → öznitelik vektörü
     *                     memoizasyon haritası
     * @return {@link Fundamentals} ve bar'lardan türetilip normalize edilmiş ve seçilen
     *         analiz tipinin kümesine kesilmiş öznitelik vektörü
     * @implNote İlk çağrıda temel veriler yalnızca analiz tipi gerektiriyorsa Yahoo'dan
     *           çekilir ({@link AnalysisType#usesFundamental()}); vektör
     *           {@link FeatureVector#fromBars} ile kurulup normalize edilir ve analiz
     *           tipinin boyutuna kesilir, sonraki çağrılar haritadan döner.
     */
    private double[] featuresCached(String symbol, List<Bar> bars, AnalysisType analysisType,
                                    Map<String, double[]> featureCache) {
        return featureCache.computeIfAbsent(symbol, s -> {
            Fundamentals f = analysisType.usesFundamental() ? yahoo.fetchFundamentals(s) : null;
            return FeatureVector.fromBars(f, bars).normalize().toArray(analysisType);
        });
    }
}