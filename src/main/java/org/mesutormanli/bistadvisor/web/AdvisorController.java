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
 * REST API denetleyicisi. Web arayüzüne portföy yönetimi, analiz ve onay
 * işlemleri için HTTP uç noktaları sunar.
 * <p>
 * Tüm uç noktalar {@code /api} ön eki altındadır.
 */
@RestController
@RequestMapping("/api")
public class AdvisorController {
    /** Geçerli hisse sembolü biçimi (BIST kodları: harf/rakam). */
    private static final java.util.regex.Pattern SYMBOL_PATTERN =
            java.util.regex.Pattern.compile("[A-Z0-9]{1,15}");

    private final PortfolioService portfolioService;
    private final DailyAdvisor dailyAdvisor;
    private final BistIndices bistIndices;

    /**
     * {@code AdvisorController} servisini kurar. Bağımlılıklar Spring tarafından enjekte edilir.
     */
    public AdvisorController(PortfolioService portfolioService, DailyAdvisor dailyAdvisor,
                            BistIndices bistIndices) {
        this.portfolioService = portfolioService;
        this.dailyAdvisor = dailyAdvisor;
        this.bistIndices = bistIndices;
    }

    /**
     * Uygulama yapılandırmasını döndürür: mevcut modlar, modeller, endeksler
     * ve seçili değerler.
     *
     * @return yapılandırma haritası
     */
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

    /**
     * Portföy durumunu döndürür.
     *
     * @return {@link PortfolioState} nesnesi
     */
    @GetMapping("/portfolio")
    public PortfolioState portfolio() { return portfolioService.getState(); }

    /**
     * Portföyün görselleştirme için zenginleştirilmiş görünümünü döndürür:
     * her pozisyon için güncel fiyat, kâr/zarar bilgileri ve toplam değerler.
     *
     * @return portföy görünüm haritası
     */
    @GetMapping("/portfolio-view")
    public Map<String, Object> portfolioView() {
        PortfolioState s = portfolioService.getState();
        Map<String, Double> prices = dailyAdvisor.currentPrices();
        double totalInvested = 0, totalCurrent = 0;
        var rows = new ArrayList<Map<String, Object>>();
        for (Position p : s.positions) {
            double cur = prices.getOrDefault(p.symbol(), p.avgCost());
            double costTotal = p.lots() * p.avgCost();
            double curTotal = p.lots() * cur;
            totalInvested += costTotal;
            totalCurrent += curTotal;
            double pnlPct = p.avgCost() > 0 ? (cur - p.avgCost()) / p.avgCost() : 0.0;
            rows.add(Map.of(
                    "symbol", p.symbol(), "lots", p.lots(), "avgCost", p.avgCost(),
                    "costTotal", costTotal, "currentPrice", cur, "currentTotal", curTotal,
                    "pnlPct", pnlPct, "pnlTl", curTotal - costTotal
            ));
        }
        return Map.of(
                "budget", s.budget, "advisorMode", s.advisorMode, "modelType", s.modelType,
                "positions", rows, "availableCash", portfolioService.availableCash(prices),
                "totalInvested", totalInvested, "totalCurrent", totalCurrent
        );
    }

    /**
     * Portföy durumunu günceller (toplam sermaye, mod, model, endeks, pozisyonlar).
     * Gelen veri yapısal olarak geçersizse hiçbir şey yazılmaz ve
     * {@code {"status":"error"}} döndürülür. İhmal edilebilir iş kuralları ihlalinde
     * (ör. maliyetin sermâyeyi aşması) kayıt yapılır ve {@code {"status":"warning"}}
     * döndürülür.
     *
     * @param incoming yeni portföy durumu
     * @return işlem sonucu ({@code status: ok/warning/error})
     */
    @PostMapping("/portfolio")
    public Map<String, String> savePortfolio(@RequestBody PortfolioState incoming) {
        List<String> invalid = structuralErrors(incoming);
        if (!invalid.isEmpty()) {
            return Map.of("status", "error", "message", String.join(" | ", invalid));
        }
        portfolioService.updateState(state -> {
            if (incoming.budget > 0) state.budget = incoming.budget;
            if (incoming.advisorMode != null) state.advisorMode = incoming.advisorMode;
            if (incoming.modelType != null) state.modelType = incoming.modelType;
            if (incoming.selectedIndex != null && bistIndices.containsIndex(incoming.selectedIndex))
                state.selectedIndex = incoming.selectedIndex.toUpperCase();
            if (incoming.positions != null) state.positions = new ArrayList<>(incoming.positions);
        });
        Map<String, String> r = new HashMap<>();
        String validationError = portfolioService.validatePortfolio();
        if (validationError != null) {
            r.put("status", "warning");
            r.put("message", validationError);
        } else {
            r.put("status", "ok");
        }
        return r;
    }

    /**
     * Gelen portföy durumu için yapısal doğrulama yapar: sembol biçimi, pozitif lot,
     * geçerli maliyet ve pozitif/sonlu sermaye. Uygulama tarafında {@code NaN}/{@code Infinity}
     * üretebilecek verileri (ör. sıfır maliyet) engeller.
     *
     * @param incoming istek gövdesindeki portföy durumu
     * @return hata mesajları; boşsa girdi geçerlidir
     */
    private List<String> structuralErrors(PortfolioState incoming) {
        List<String> errors = new ArrayList<>();
        if (!Double.isFinite(incoming.budget) || incoming.budget < 0) {
            errors.add("Gecersiz butce: " + incoming.budget);
        }
        if (incoming.positions != null) {
            for (Position p : incoming.positions) {
                if (p == null || p.symbol() == null || p.symbol().isBlank()) {
                    errors.add("Pozisyonda sembol eksik");
                    continue;
                }
                if (!SYMBOL_PATTERN.matcher(p.symbol().trim().toUpperCase()).matches()) {
                    errors.add("Gecersiz sembol: " + p.symbol());
                }
                if (p.lots() <= 0) {
                    errors.add(p.symbol() + ": lot sayisi pozitif olmali (" + p.lots() + ")");
                }
                if (!(p.avgCost() > 0) || !Double.isFinite(p.avgCost())) {
                    errors.add(p.symbol() + ": birim maliyet pozitif olmali (" + p.avgCost() + ")");
                }
            }
        }
        return errors;
    }

    /**
     * Günlük analizi çalıştırır ve sonucu döndürür.
     *
     * @return {@link AnalysisResult} nesnesi
     */
    @PostMapping("/analyze")
    public AnalysisResult analyze() { return dailyAdvisor.analyze(); }

    /**
     * Onaylanmış bir işlemi temsil eden istek gövdesi kaydı.
     *
     * @param symbol hisse sembolü
     * @param action işlem türü (AL/SAT)
     * @param lots   lot miktarı
     * @param price  işlem fiyatı
     */
    public record ConfirmReq(String symbol, String action, int lots, double price) {}

    /**
     * Bir liste onaylanmış işlemi portföye uygular.
     *
     * @param reqs onaylanmış işlem listesi
     * @return işlem sonucu (uygulanan/başarısız sayıları)
     */
    @PostMapping("/confirm")
    public Map<String, String> confirm(@RequestBody List<ConfirmReq> reqs) {
        int applied = 0;
        int failed = 0;
        for (ConfirmReq r : reqs) {
            if (portfolioService.applyTransaction(r.symbol(), r.action(), r.lots(), r.price())) {
                applied++;
            } else {
                failed++;
            }
        }
        portfolioService.save(portfolioService.getState());
        Map<String, String> res = new HashMap<>();
        res.put("status", "ok");
        res.put("applied", String.valueOf(applied));
        res.put("failed", String.valueOf(failed));
        return res;
    }

    /**
     * Bekleyen işlemleri döndürür: satış önerileri ve alım önerileri.
     *
     * @return bekleyen işlem listesi
     */
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

    /**
     * Bir {@link Recommendation} nesnesini haritaya dönüştürür.
     *
     * @param r öneri kaydı
     * @return harita temsili
     */
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
