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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Portfoy ve ML modelini birlestirerek gunluk AL/SAT/TUT onerileri uretir. */
@Service
public class DailyAdvisor {
    private static final Logger log = LoggerFactory.getLogger(DailyAdvisor.class);

    private final BistIndices bistIndices;
    private final YahooClient yahoo;
    private final CacheStore cacheStore;
    private final ModelTrainer modelTrainer;
    private final PortfolioService portfolioService;

    public DailyAdvisor(BistIndices bistIndices, YahooClient yahoo,
                        CacheStore cacheStore,
                        ModelTrainer modelTrainer, PortfolioService portfolioService) {
        this.bistIndices = bistIndices;
        this.yahoo = yahoo;
        this.cacheStore = cacheStore;
        this.modelTrainer = modelTrainer;
        this.portfolioService = portfolioService;
    }

    /** Tek bir oneri kaydi. */
    public record Recommendation(int index, String symbol, String action,
                                 int lots, double price, double score, String note) {}

    /** Analiz sonucu: mevcut portfoy durumu + alim onerileri. */
    public record AnalysisResult(List<Recommendation> holdings, List<Recommendation> buys,
                                 double availableCash, int positionCount,
                                 int maxPositions, int buySlots) {}

    /** Ana analiz dongusu: portfoydeki her pozisyonu ve aday hisseleri ML modeli ile degerlendirir. */
    public AnalysisResult analyze() {
        PortfolioState state = portfolioService.getState();
        AdvisorMode mode = portfolioService.advisorMode();
        ModelType modelType = portfolioService.modelType();
        ModelStrategy model = modelTrainer.getOrTrain(modelType, state.selectedIndex);

        Map<String, Double> currentPrices = new LinkedHashMap<>();
        List<Recommendation> holdings = new ArrayList<>();
        int idx = 1;

        for (Position p : state.positions) {
            List<Bar> bars = loadSeries(p.symbol);
            double price = currentPrice(bars, p.avgCost);
            currentPrices.put(p.symbol, price);
            double pnlPct = (price - p.avgCost) / p.avgCost;
            String action = "TUT";
            String note = (pnlPct >= 0 ? "+" : "") + String.format("%.2f", pnlPct * 100) + "%";
            double[] pred = model.predict(featuresFor(p.symbol, bars));
            double score = pred[1];
            if (pnlPct <= -mode.stopLossPct || (score < mode.sellScoreThreshold && pred[0] == Labeler.SELL)) {
                action = "SAT";
                note += " | skor=" + String.format("%.2f", score);
            }
            holdings.add(new Recommendation(idx++, p.symbol, action, p.lots, price, 0.0, note));
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
                if (candidates.size() >= slotsForBuy) break;
                List<Bar> bars = loadSeries(sym);
                double price = currentPrice(bars, 0.0);
                if (price <= 0) continue;
                double[] pred = model.predict(featuresFor(sym, bars));
                double score = pred[1];
                if (pred[0] == Labeler.BUY && score >= mode.buyThreshold) {
                    candidates.add(new Candidate(sym, price, score));
                }
            }
            for (Position p : state.positions) {
                List<Bar> bars = loadSeries(p.symbol);
                double price = currentPrice(bars, p.avgCost);
                double[] pred = model.predict(featuresFor(p.symbol, bars));
                double score = pred[1];
                if (pred[0] == Labeler.BUY && score >= mode.buyThreshold) {
                    candidates.add(new Candidate(p.symbol, price, score));
                }
            }
            if (!candidates.isEmpty()) {
                double totalBudget = cash * mode.riskPct;
                double totalScore = candidates.stream().mapToDouble(c -> c.score).sum();
                for (Candidate c : candidates) {
                    double alloc = totalBudget * (c.score / totalScore);
                    int lots = (int) Math.floor(alloc / c.price);
                    if (lots > 0) {
                        buys.add(new Recommendation(idx++, c.symbol, "AL", lots, c.price, c.score,
                                "skor=" + String.format("%.2f", c.score)));
                    }
                }
            }
        }

        portfolioService.updateState(s -> s.lastRunDate = LocalDate.now().toString());
        return new AnalysisResult(holdings, buys, cash, state.positions.size(),
                portfolioService.maxPositions(), slotsForBuy);
    }

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

    private double currentPrice(List<Bar> series, double fallback) {
        return series.isEmpty() ? fallback : series.getLast().close();
    }

    private double[] featuresFor(String symbol, List<Bar> bars) {
        Fundamentals f = yahoo.fetchFundamentals(symbol);
        FeatureVector fv = FeatureVector.fromBars(f, bars);
        fv.normalize();
        return fv.toArray();
    }

    /** Portfoydeki tum sembollerin guncel kapanis fiyatlarini dondurur. */
    public Map<String, Double> currentPrices() {
        Map<String, Double> prices = new LinkedHashMap<>();
        for (Position p : portfolioService.getState().positions) {
            prices.put(p.symbol, currentPrice(loadSeries(p.symbol), p.avgCost));
        }
        return prices;
    }
}
