package org.mesutormanli.bistadvisor.web;

import org.mesutormanli.bistadvisor.advisor.DailyAdvisor;
import org.mesutormanli.bistadvisor.advisor.DailyAdvisor.AnalysisResult;
import org.mesutormanli.bistadvisor.advisor.DailyAdvisor.Recommendation;
import org.mesutormanli.bistadvisor.config.AdvisorMode;
import org.mesutormanli.bistadvisor.data.BistIndices;
import org.mesutormanli.bistadvisor.config.ModelType;
import org.mesutormanli.bistadvisor.portfolio.PortfolioService;
import org.mesutormanli.bistadvisor.portfolio.PortfolioState;
import org.mesutormanli.bistadvisor.portfolio.Position;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Web arayuzu ve CLI ortak REST API.
 */
@RestController
@RequestMapping("/api")
public class AdvisorController {
    private final PortfolioService portfolioService;
    private final DailyAdvisor dailyAdvisor;
    private final BistIndices bistIndices;

    public AdvisorController(PortfolioService portfolioService, DailyAdvisor dailyAdvisor,
                            BistIndices bistIndices) {
        this.portfolioService = portfolioService;
        this.dailyAdvisor = dailyAdvisor;
        this.bistIndices = bistIndices;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> m = new HashMap<>();
        List<String> modes = new ArrayList<>();
        for (AdvisorMode am : AdvisorMode.values()) modes.add(am.name());
        List<String> models = new ArrayList<>();
        for (ModelType mt : ModelType.values()) models.add(mt.name());
        List<String> indices = bistIndices.indexNames();
        m.put("modes", modes);
        m.put("models", models);
        m.put("indices", indices);
        m.put("currentMode", portfolioService.advisorMode().name());
        m.put("currentModel", portfolioService.modelType().name());
        m.put("currentIndex", portfolioService.getState().selectedIndex);
        return m;
    }

    @GetMapping("/portfolio")
    public PortfolioState portfolio() {
        return portfolioService.getState();
    }

    /** Portfoy tablosu icin zenginlestirilmis gorunum (guncek fiyatlarla). */
    @GetMapping("/portfolio-view")
    public Map<String, Object> portfolioView() {
        PortfolioState s = portfolioService.getState();
        Map<String, Double> prices = dailyAdvisor.currentPrices();
        List<Map<String, Object>> rows = new ArrayList<>();
        double totalInvested = 0, totalCurrent = 0;
        for (Position p : s.positions) {
            double cur = prices.getOrDefault(p.symbol, p.avgCost);
            double costTotal = p.lots * p.avgCost;
            double curTotal = p.lots * cur;
            totalInvested += costTotal;
            totalCurrent += curTotal;
            double pnlPct = (cur - p.avgCost) / p.avgCost;
            double pnlTl = curTotal - costTotal;
            Map<String, Object> r = new HashMap<>();
            r.put("symbol", p.symbol);
            r.put("lots", p.lots);
            r.put("avgCost", p.avgCost);
            r.put("costTotal", costTotal);
            r.put("currentPrice", cur);
            r.put("currentTotal", curTotal);
            r.put("pnlPct", pnlPct);
            r.put("pnlTl", pnlTl);
            rows.add(r);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("budget", s.budget);
        out.put("advisorMode", s.advisorMode);
        out.put("modelType", s.modelType);
        out.put("positions", rows);
        out.put("availableCash", portfolioService.availableCash(prices));
        out.put("totalInvested", totalInvested);
        out.put("totalCurrent", totalCurrent);
        return out;
    }

    @PostMapping("/portfolio")
    public Map<String, String> savePortfolio(@RequestBody PortfolioState incoming) {
        portfolioService.updateState(state -> {
            if (incoming.budget > 0) state.budget = incoming.budget;
            if (incoming.advisorMode != null) state.advisorMode = incoming.advisorMode;
            if (incoming.modelType != null) state.modelType = incoming.modelType;
            if (incoming.selectedIndex != null && bistIndices.containsIndex(incoming.selectedIndex))
                state.selectedIndex = incoming.selectedIndex.toUpperCase();
            if (incoming.positions != null) state.positions = new ArrayList<>(incoming.positions);
        });
        Map<String, String> r = new HashMap<>();
        r.put("status", "ok");
        return r;
    }

    @PostMapping("/analyze")
    public AnalysisResult analyze() {
        return dailyAdvisor.analyze();
    }

    public record ConfirmReq(String symbol, String action, int lots, double price) {
    }

    @PostMapping("/confirm")
    public Map<String, String> confirm(@RequestBody List<ConfirmReq> reqs) {
        int[] applied = {0};
        portfolioService.updateState(state -> {
            for (ConfirmReq r : reqs) {
                portfolioService.applyTransaction(r.symbol(), r.action(), r.lots(), r.price());
                applied[0]++;
            }
        });
        Map<String, String> res = new HashMap<>();
        res.put("status", "ok");
        res.put("applied", String.valueOf(applied[0]));
        return res;
    }

    /** Onay icin AL ve SAT onerilerini (TUT haric) tek listede doner. */
    @GetMapping("/pending")
    public List<Map<String, Object>> pending() {
        AnalysisResult a = dailyAdvisor.analyze();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Recommendation r : a.holdings()) {
            if ("SAT".equals(r.action())) out.add(toMap(r));
        }
        for (Recommendation r : a.buys()) {
            if ("AL".equals(r.action())) out.add(toMap(r));
        }
        return out;
    }

    private Map<String, Object> toMap(Recommendation r) {
        Map<String, Object> m = new HashMap<>();
        m.put("index", r.index());
        m.put("symbol", r.symbol());
        m.put("action", r.action());
        m.put("lots", r.lots());
        m.put("price", r.price());
        m.put("note", r.note());
        return m;
    }
}
