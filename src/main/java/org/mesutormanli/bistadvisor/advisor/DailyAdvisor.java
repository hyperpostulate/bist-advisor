package org.mesutormanli.bistadvisor.advisor;

import org.mesutormanli.bistadvisor.advisor.AllocationPlanner.CandidateInput;
import org.mesutormanli.bistadvisor.advisor.AllocationPlanner.HoldingInput;
import org.mesutormanli.bistadvisor.advisor.AllocationPlanner.Plan;
import org.mesutormanli.bistadvisor.advisor.AllocationPlanner.Planned;
import org.mesutormanli.bistadvisor.config.AdvisorMode;
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
 * Günlük öneri orkestratörü: veri yükler, model tahminlerini üretir ve karar
 * mantığını {@link AllocationPlanner}'a devreder.
 * <p>
 * Bir analiz turunda her sembol için fiyat serisi ve özellik vektörü <em>bir kez</em>
 * hesaplanır; fiyat verisi alınamayan semboller için sahte fiyat üretilmez,
 * "veri yok" bildirilir.
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
     * {@code DailyAdvisor} servisini kurar. Bağımlılıklar Spring tarafından enjekte edilir.
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
     * Tek bir hisse senedi önerisini temsil eder.
     *
     * @param index  sıra numarası
     * @param symbol hisse sembolü
     * @param action işlem türü (AL/SAT/TUT)
     * @param lots   lot miktarı
     * @param price  güncel fiyat ({@code null} = fiyat verisi yok)
     * @param score  model güven skoru ({@code null} = tahmin yok)
     * @param note   açıklama notu
     */
    public record Recommendation(int index, String symbol, String action,
                                 int lots, Double price, Double score, String note) {}

    /**
     * Günlük analiz sonucunu kapsüller.
     *
     * @param holdings       mevcut portföy pozisyonları için öneriler
     * @param buys           alım önerileri
     * @param availableCash  kullanılabilir nakit
     * @param positionCount  mevcut pozisyon sayısı
     * @param maxPositions   maksimum pozisyon limiti
     * @param buySlots       doldurulabilecek <em>yeni</em> pozisyon sayısı
     * @param warnings       kullanıcıya gösterilecek uyarılar (ör. veri yok)
     */
    public record AnalysisResult(List<Recommendation> holdings, List<Recommendation> buys,
                                 double availableCash, int positionCount,
                                 int maxPositions, int buySlots, List<String> warnings) {}

    /**
     * Günlük portföy analizini çalıştırır:
     * <ul>
     *   <li>Mevcut pozisyonlar için SAT/TUT kararlarını üretir</li>
     *   <li>Uygun hisseler için AL önerilerini sıralar ve bütçe dağıtımı yapar</li>
     *   <li>Son çalışma tarihini günceller</li>
     * </ul>
     *
     * @return analiz sonucu ({@code AnalysisResult})
     */
    public AnalysisResult analyze() {
        PortfolioState state = portfolioService.getState();
        AdvisorMode mode = portfolioService.advisorMode();
        ModelType modelType = portfolioService.modelType();
        ModelStrategy model = modelTrainer.getOrTrain(modelType, state.selectedIndex);

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
                double[] pred = model.predict(featuresCached(p.symbol(), bars, featureCache));
                predClass = (int) pred[0];
                score = pred[1];
            }
            holdingInputs.add(new HoldingInput(p.symbol(), p.lots(), p.avgCost(),
                    price, predClass, score));
        }

        // Alım adayları: endeksteki diğer hisseler + mevcut pozisyonlara ekleme.
        // Eleneleme (SAT çakışması, slot, eşik) AllocationPlanner'da yapılır.
        Set<String> held = new HashSet<>();
        for (Position p : state.positions) held.add(p.symbol());

        List<CandidateInput> candidateInputs = new ArrayList<>();
        for (String sym : bistIndices.symbolsOf(state.selectedIndex)) {
            if (held.contains(sym)) continue;
            List<Bar> bars = loadSeriesCached(sym, barsCache);
            Double price = currentPrice(bars);
            if (price == null) continue;
            double[] pred = model.predict(featuresCached(sym, bars, featureCache));
            candidateInputs.add(new CandidateInput(sym, price, pred[1], (int) pred[0], false));
        }
        for (Position p : state.positions) {
            List<Bar> bars = loadSeriesCached(p.symbol(), barsCache);
            Double price = currentPrice(bars);
            if (price == null) continue;
            double[] pred = model.predict(featuresCached(p.symbol(), bars, featureCache));
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

        portfolioService.updateState(s -> s.lastRunDate = LocalDate.now().toString());
        return new AnalysisResult(holdings, buys, portfolioService.availableCash(),
                state.positions.size(), portfolioService.maxPositions(),
                plan.buySlots(), plan.warnings());
    }

    /**
     * Portföydeki tüm pozisyonlar için güncel fiyatları harita olarak döndürür.
     * Fiyat verisi alınamayan semboller haritada yer almaz ("veri yok" kabul edilir).
     *
     * @return sembol -> güncel fiyat eşlemesi (yalnızca fiyat bilinenler)
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
     * Belirtilen hisse için fiyat serisini önbellekten (harita üzerinden) döndürür.
     * Seri daha önce yüklenmemişse {@link #loadSeries(String)} çağrılır.
     */
    private List<Bar> loadSeriesCached(String symbol, Map<String, List<Bar>> cache) {
        return cache.computeIfAbsent(symbol, this::loadSeries);
    }

    /**
     * Belirtilen hisse için fiyat serisini yükler. Önce önbelleği kontrol eder;
     * taze değilse Yahoo Finance'den çeker ve önbelleğe yazar.
     *
     * @param symbol hisse sembolü
     * @return fiyat çubukları listesi
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
     * Fiyat serisinin son kapanış değerini döndürür. Seri boşsa (veri yoksa)
     * {@code null} döner — sahte fiyat üretilmez.
     */
    private Double currentPrice(List<Bar> series) {
        return series.isEmpty() ? null : series.getLast().close();
    }

    /**
     * Bir hisse için normalize öznitelik vektörünü hesaplar ve tur içi önbelleğe alır;
     * aynı sembol için hem holding hem aday döngülerinde tek hesaplama yapılır.
     *
     * @param symbol      hisse sembolü
     * @param bars        fiyat çubukları serisi
     * @param featureCache tur içi özellik önbelleği
     * @return 11 boyutlu normalleştirilmiş öznitelik dizisi
     */
    private double[] featuresCached(String symbol, List<Bar> bars, Map<String, double[]> featureCache) {
        return featureCache.computeIfAbsent(symbol, s -> {
            Fundamentals f = yahoo.fetchFundamentals(s);
            return FeatureVector.fromBars(f, bars).normalize().toArray();
        });
    }
}