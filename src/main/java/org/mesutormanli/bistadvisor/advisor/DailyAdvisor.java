package org.mesutormanli.bistadvisor.advisor;

import org.mesutormanli.bistadvisor.config.AdvisorMode;
import org.mesutormanli.bistadvisor.config.ModelType;
import org.mesutormanli.bistadvisor.data.BistIndices;
import org.mesutormanli.bistadvisor.data.CacheStore;
import org.mesutormanli.bistadvisor.data.PriceScraper;
import org.mesutormanli.bistadvisor.data.YahooFundamentalsScraper;
import org.mesutormanli.bistadvisor.features.FeatureVector;
import org.mesutormanli.bistadvisor.features.TechnicalFeatures;
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

/**
 * Gunluk öneri seti üretir. Hem CLI 'run' hem web '/api/analyze' bunu kullanir.
 */
@Service
public class DailyAdvisor {
    private static final Logger log = LoggerFactory.getLogger(DailyAdvisor.class);

    private final BistIndices bistIndices;
    private final PriceScraper priceScraper;
    private final YahooFundamentalsScraper fundamentalsScraper;
    private final CacheStore cacheStore;
    private final ModelTrainer modelTrainer;
    private final PortfolioService portfolioService;

    public DailyAdvisor(BistIndices bistIndices, PriceScraper priceScraper,
                        YahooFundamentalsScraper fundamentalsScraper,
                        CacheStore cacheStore,
                        ModelTrainer modelTrainer, PortfolioService portfolioService) {
        this.bistIndices = bistIndices;
        this.priceScraper = priceScraper;
        this.fundamentalsScraper = fundamentalsScraper;
        this.cacheStore = cacheStore;
        this.modelTrainer = modelTrainer;
        this.portfolioService = portfolioService;
    }

    public record Recommendation(int index, String symbol, String action,
                                 int lots, double price, double score,
                                 String note) {
    }

    public record AnalysisResult(List<Recommendation> holdings,
                                 List<Recommendation> buys,
                                 double availableCash, int positionCount,
                                 int maxPositions, int buySlots) {
    }

    public AnalysisResult analyze() {
        PortfolioState state = portfolioService.getState();
        AdvisorMode mode = portfolioService.advisorMode();
        ModelType modelType = portfolioService.modelType();
        ModelStrategy model = modelTrainer.getOrTrain(modelType, state.selectedIndex);

        Map<String, Double> currentPrices = new LinkedHashMap<>();
        List<Recommendation> holdings = new ArrayList<>();
        int idx = 1;

        for (Position p : state.positions) {
            List<String> series = loadSeries(p.symbol);
            double price = currentPrice(series, p.avgCost);
            currentPrices.put(p.symbol, price);
            double pnlPct = (price - p.avgCost) / p.avgCost;
            String action = "TUT";
            String note = (pnlPct >= 0 ? "+" : "") + String.format("%.2f", pnlPct * 100) + "%";
            double[] pred = model.predict(featuresFor(p.symbol, series));
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
        if (slotsForBuy > 0) {
            double budgetForBuy = cash * mode.riskPct;
            for (String sym : bistIndices.symbolsOf(state.selectedIndex)) {
                if (currentPrices.containsKey(sym)) continue; // zaten portfoyde
                if (buys.size() >= slotsForBuy) break;
                List<String> series = loadSeries(sym);
                double price = currentPrice(series, 0.0);
                if (price <= 0) continue;
                double[] pred = model.predict(featuresFor(sym, series));
                double score = pred[1];
                if (pred[0] == Labeler.BUY && score >= mode.buyThreshold) {
                    int lots = (int) Math.floor(budgetForBuy / price);
                    if (lots > 0) {
                        buys.add(new Recommendation(idx++, sym, "AL", lots, price, score,
                                "skor=" + String.format("%.2f", score)));
                    }
                }
            }
        }

        state.lastRunDate = LocalDate.now().toString();
        return new AnalysisResult(holdings, buys, cash, state.positions.size(),
                portfolioService.maxPositions(), slotsForBuy);
    }

    private List<String> loadSeries(String symbol) {
        LocalDate today = LocalDate.now();
        if (!cacheStore.hasFresh(symbol, today)) {
            List<String> fetched = priceScraper.fetch(symbol);
            if (!fetched.isEmpty()) cacheStore.writePrice(symbol, fetched);
        }
        return cacheStore.readPrice(symbol);
    }

    private double currentPrice(List<String> series, double fallback) {
        if (series.isEmpty()) return fallback;
        String last = series.getLast();
        String[] p = last.split(",");
        try {
            // format: tarih,acilis,yuksek,dusuk,kapanis,hacim
            return Double.parseDouble(p[4]);
        } catch (Exception e) {
            return fallback;
        }
    }

    private double[] featuresFor(String symbol, List<String> series) {
        List<TechnicalFeatures.Bar> bars = TechnicalFeatures.toBars(series);
        Map<String, Double> fundamentals = fundamentalsScraper.fetch(symbol);
        FeatureVector fv = FeatureVector.fromBars(fundamentals, bars);
        fv.normalize();
        return fv.toArray();
    }

    /** Portfoydeki sembollerin gunluk kapanis fiyatlarini (canli) dondurur. */
    public Map<String, Double> currentPrices() {
        Map<String, Double> prices = new LinkedHashMap<>();
        for (Position p : portfolioService.getState().positions) {
            List<String> series = loadSeries(p.symbol);
            prices.put(p.symbol, currentPrice(series, p.avgCost));
        }
        return prices;
    }
}
