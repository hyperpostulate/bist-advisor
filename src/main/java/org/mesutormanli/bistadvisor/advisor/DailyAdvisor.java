package org.mesutormanli.bistadvisor.advisor;

import org.mesutormanli.bistadvisor.config.AdvisorMode;
import org.mesutormanli.bistadvisor.config.ModelType;
import org.mesutormanli.bistadvisor.data.BistIndices;
import org.mesutormanli.bistadvisor.data.CacheStore;
import org.mesutormanli.bistadvisor.data.YahooClient;
import org.mesutormanli.bistadvisor.data.YahooClient.Fundamentals;
import org.mesutormanli.bistadvisor.features.FeatureVector;
import org.mesutormanli.bistadvisor.features.TechnicalFeatures;
import org.mesutormanli.bistadvisor.features.TechnicalFeatures.Bar;
import org.mesutormanli.bistadvisor.model.Labeler;
import org.mesutormanli.bistadvisor.model.ModelStrategy;
import org.mesutormanli.bistadvisor.model.ModelTrainer;
import org.mesutormanli.bistadvisor.portfolio.PortfolioService;
import org.mesutormanli.bistadvisor.portfolio.PortfolioState;
import org.mesutormanli.bistadvisor.portfolio.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DailyAdvisor {
    private static final Logger log = LoggerFactory.getLogger(DailyAdvisor.class);

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
     * @param price  güncel fiyat
     * @param score  model güven skoru
     * @param note   açıklama notu
     */
    public record Recommendation(int index, String symbol, String action,
                                 int lots, double price, double score, String note) {}

    /**
     * Günlük analiz sonucunu kapsüller.
     *
     * @param holdings       mevcut portföy pozisyonları için öneriler
     * @param buys           alım önerileri
     * @param availableCash  kullanılabilir nakit
     * @param positionCount  mevcut pozisyon sayısı
     * @param maxPositions   maksimum pozisyon limiti
     * @param buySlots       doldurulabilecek pozisyon sayısı
     */
    public record AnalysisResult(List<Recommendation> holdings, List<Recommendation> buys,
                                 double availableCash, int positionCount,
                                 int maxPositions, int buySlots) {}

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
        Map<String, Double> currentPrices = new LinkedHashMap<>();
        List<Recommendation> holdings = new ArrayList<>();
        int idx = 1;

        for (Position p : state.positions) {
            List<Bar> bars = loadSeriesCached(p.symbol(), barsCache);
            double price = currentPrice(bars, p.avgCost());
            currentPrices.put(p.symbol(), price);
            double pnlPct = (price - p.avgCost()) / p.avgCost();
            String action = "TUT";
            String note = (pnlPct >= 0 ? "+" : "") + String.format("%.2f", pnlPct * 100) + "%";
            double[] pred = model.predict(featuresFor(p.symbol(), bars));
            double score = pred[1];
            if (pnlPct <= -mode.stopLossPct || (pred[0] == Labeler.SELL && score >= mode.sellScoreThreshold)) {
                action = "SAT";
                note += " | skor=" + String.format("%.2f", score);
            }
            holdings.add(new Recommendation(idx++, p.symbol(), action, p.lots(), price, 0.0, note));
        }

        double cash = portfolioService.availableCash(currentPrices);
        List<Recommendation> buys = new ArrayList<>();
        long satCount = holdings.stream().filter(h -> "SAT".equals(h.action())).count();
        int slotsForBuy = portfolioService.buySlotsAfter((int) satCount);
        if (slotsForBuy > 0 || !state.positions.isEmpty()) {
            record Candidate(String symbol, double price, double score) {}
            List<Candidate> candidates = new ArrayList<>();

            for (String sym : bistIndices.symbolsOf(state.selectedIndex)) {
                if (currentPrices.containsKey(sym)) continue;
                List<Bar> bars = loadSeriesCached(sym, barsCache);
                double price = currentPrice(bars, 0.0);
                if (price <= 0) continue;
                double[] pred = model.predict(featuresFor(sym, bars));
                double score = pred[1];
                if (pred[0] == Labeler.BUY && score >= mode.buyThreshold) {
                    candidates.add(new Candidate(sym, price, score));
                }
            }
            for (Position p : state.positions) {
                List<Bar> bars = loadSeriesCached(p.symbol(), barsCache);
                double price = currentPrice(bars, p.avgCost());
                double[] pred = model.predict(featuresFor(p.symbol(), bars));
                double score = pred[1];
                if (pred[0] == Labeler.BUY && score >= mode.buyThreshold) {
                    candidates.add(new Candidate(p.symbol(), price, score));
                }
            }

            candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
            List<Candidate> top = candidates.size() <= slotsForBuy ? candidates : candidates.subList(0, slotsForBuy);

            if (!top.isEmpty()) {
                double totalBudget = cash * mode.riskPct;
                double totalScore = top.stream().mapToDouble(c -> c.score).sum();
                if (totalScore > 0) {
                    for (Candidate c : top) {
                        double alloc = totalBudget * (c.score / totalScore);
                        int lots = (int) Math.floor(alloc / c.price);
                        if (lots > 0) {
                            buys.add(new Recommendation(idx++, c.symbol, "AL", lots, c.price, c.score,
                                    "skor=" + String.format("%.2f", c.score)));
                        }
                    }
                }
            }
        }

        portfolioService.updateState(s -> s.lastRunDate = LocalDate.now().toString());
        return new AnalysisResult(holdings, buys, cash, state.positions.size(),
                portfolioService.maxPositions(), slotsForBuy);
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
        LocalDate today = LocalDate.now();
        if (!cacheStore.hasFresh(symbol, today)) {
            List<Bar> fetched = yahoo.fetchPrices(symbol);
            if (!fetched.isEmpty()) {
                cacheStore.writeLines(symbol, fetched.stream()
                        .map(b -> b.date() + "," + b.close() + "," + (long) b.volume()).toList());
            }
        }
        return TechnicalFeatures.toBars(cacheStore.readLines(symbol));
    }

    /**
     * Fiyat serisinin son kapanış değerini döndürür. Seri boşsa {@code fallback} kullanılır.
     */
    private double currentPrice(List<Bar> series, double fallback) {
        return series.isEmpty() ? fallback : series.getLast().close();
    }

    /**
     * Bir hisse senedi için normalleştirilmiş öznitelik vektörünü hesaplar.
     * Teknik göstergeleri ve temel verileri (F/K, PD/DD vb.) birleştirir.
     *
     * @param symbol hisse sembolü
     * @param bars   fiyat çubukları serisi
     * @return 11 boyutlu normalleştirilmiş öznitelik dizisi
     */
    private double[] featuresFor(String symbol, List<Bar> bars) {
        Fundamentals f = yahoo.fetchFundamentals(symbol);
        FeatureVector fv = FeatureVector.fromBars(f, bars);
        fv.normalize();
        return fv.toArray();
    }

    /**
     * Portföydeki tüm pozisyonlar için güncel fiyatları harita olarak döndürür.
     *
     * @return sembol -> güncel fiyat eşlemesi
     */
    public Map<String, Double> currentPrices() {
        Map<String, Double> prices = new LinkedHashMap<>();
        for (Position p : portfolioService.getState().positions) {
            prices.put(p.symbol(), currentPrice(loadSeries(p.symbol()), p.avgCost()));
        }
        return prices;
    }
}
