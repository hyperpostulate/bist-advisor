package org.mesutormanli.bistadvisor.web;

import org.mesutormanli.bistadvisor.advisor.DailyAdvisor;
import org.mesutormanli.bistadvisor.advisor.DailyAdvisor.AnalysisResult;
import org.mesutormanli.bistadvisor.advisor.DailyAdvisor.Recommendation;
import org.mesutormanli.bistadvisor.config.AdvisorMode;
import org.mesutormanli.bistadvisor.config.AnalysisType;
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
 * Tek sayfalık arayüzün ({@code static/index.html}) kullandığı REST yüzeyini sunan denetleyici.
 * <p>
 * Tüm uç noktalar {@code /api} altındadır: {@code GET /api/config},
 * {@code GET /api/portfolio}, {@code GET /api/portfolio-view}, {@code POST /api/portfolio},
 * {@code POST /api/analyze}, {@code POST /api/confirm} ve {@code GET /api/pending}.
 * Semboller {@code [A-Z0-9]{1,15}} deseniyle doğrulanır.
 */
@RestController
@RequestMapping("/api")
public class AdvisorController {
    private static final java.util.regex.Pattern SYMBOL_PATTERN =
            java.util.regex.Pattern.compile("[A-Z0-9]{1,15}");

    private final PortfolioService portfolioService;
    private final DailyAdvisor dailyAdvisor;
    private final BistIndices bistIndices;

    /**
     * Denetleyiciyi bağımlılıklarıyla kurar.
     *
     * @param portfolioService portföyün bellek içi sahibi ve durum dosyası (state.yaml) kalıcılığı
     * @param dailyAdvisor     günlük analiz ve öneri üreticisi
     * @param bistIndices      BIST endeks kataloğu (endeks adı doğrulaması için)
     */
    public AdvisorController(PortfolioService portfolioService, DailyAdvisor dailyAdvisor,
                            BistIndices bistIndices) {
        this.portfolioService = portfolioService;
        this.dailyAdvisor = dailyAdvisor;
        this.bistIndices = bistIndices;
    }

    /**
     * Yapılandırmayı döndürür: modeller, modlar, analiz tipleri ve endeks listesi ile aktif
     * seçimler.
     * <p>
     * REST uç noktası: {@code GET /api/config}.
     *
     * @return liste ve aktif seçimleri ({@code currentMode}, {@code currentModel},
     *         {@code currentAnalysisType}, {@code currentIndex}) içeren harita
     */
    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> m = new HashMap<>();
        List<String> modes = new ArrayList<>();
        for (AdvisorMode am : AdvisorMode.values()) modes.add(am.name());
        List<String> models = new ArrayList<>();
        for (ModelType mt : ModelType.values()) models.add(mt.name());
        List<String> analysisTypes = new ArrayList<>();
        for (AnalysisType at : AnalysisType.values()) analysisTypes.add(at.name());
        List<String> indices = bistIndices.indexNames();
        m.put("modes", modes);
        m.put("models", models);
        m.put("analysisTypes", analysisTypes);
        m.put("indices", indices);
        m.put("currentMode", portfolioService.advisorMode().name());
        m.put("currentModel", portfolioService.modelType().name());
        m.put("currentAnalysisType", portfolioService.analysisType().name());
        m.put("currentIndex", portfolioService.getState().selectedIndex);
        return m;
    }

    /**
     * Ham portföy durumunu döndürür.
     * <p>
     * REST uç noktası: {@code GET /api/portfolio}.
     *
     * @return durum dosyası (state.yaml) ile aynı {@link PortfolioState}
     */
    @GetMapping("/portfolio")
    public PortfolioState portfolio() { return portfolioService.getState(); }

    /**
     * Pozisyon başına kâr/zarar görünümünü döndürür.
     * <p>
     * REST uç noktası: {@code GET /api/portfolio-view}. Her satırda maliyet, anlık değer
     * ve kâr/zarar (TL ve %) verilir; ayrıca toplam yatırılan, toplam anlık değer,
     * özkaynak (nakit + anlık değer) ve fiyatı çekilemeyen semboller ({@code missingPrices})
     * döndürülür. Fiyatı olmayan pozisyonda kâr alanları {@code null} olur.
     *
     * @return pozisyon satırlarını ve toplamları içeren harita
     */
    @GetMapping("/portfolio-view")
    public Map<String, Object> portfolioView() {
        PortfolioState s = portfolioService.getState();
        Map<String, Double> prices = dailyAdvisor.currentPrices();
        double totalInvested = 0, totalCurrent = 0;
        var rows = new ArrayList<Map<String, Object>>();
        var missingPrices = new ArrayList<String>();
        for (Position p : s.positions) {
            Double cur = prices.get(p.symbol());
            if (cur == null) missingPrices.add(p.symbol());
            double costTotal = p.lots() * p.avgCost();
            totalInvested += costTotal;
            Map<String, Object> row = new HashMap<>();
            row.put("symbol", p.symbol());
            row.put("lots", p.lots());
            row.put("avgCost", p.avgCost());
            row.put("costTotal", costTotal);
            row.put("currentPrice", cur);
            if (cur != null) {
                double curTotal = p.lots() * cur;
                totalCurrent += curTotal;
                row.put("currentTotal", curTotal);
                row.put("pnlPct", p.avgCost() > 0 ? (cur - p.avgCost()) / p.avgCost() : 0.0);
                row.put("pnlTl", curTotal - costTotal);
            } else {
                row.put("currentTotal", null);
                row.put("pnlPct", null);
                row.put("pnlTl", null);
            }
            rows.add(row);
        }
        double cash = portfolioService.availableCash();
        Map<String, Object> out = new HashMap<>();
        out.put("budget", s.budget);
        out.put("advisorMode", s.advisorMode);
        out.put("modelType", s.modelType);
        out.put("analysisType", s.analysisType);
        out.put("positions", rows);
        out.put("availableCash", cash);
        out.put("totalInvested", totalInvested);
        out.put("totalCurrent", totalCurrent);
        out.put("equity", cash + totalCurrent);
        out.put("missingPrices", missingPrices);
        return out;
    }

    /**
     * Gelen portföy taslağını doğrulayıp kaydeder.
     * <p>
     * REST uç noktası: {@code POST /api/portfolio}. Önce {@link #structuralErrors} ile
     * yapısal doğrulama yapılır; hata varsa yanıt {@code {"status":"error","message":...}}
     * olur ve portföy KAYDEDİLMEZ. Geçerliyse {@link PortfolioService#updatePortfolio}
     * ile kaydedilir (endeks adı yalnızca {@link BistIndices} içinde varsa kabul edilir;
     * analiz tipi geçersizse {@link AnalysisType#fromKey} ile varsayılana düşer);
     * ardından {@link PortfolioService#validatePortfolio} sonucuna göre durum
     * {@code warning} veya {@code ok} olur.
     *
     * @param incoming istemciden gelen portföy taslağı
     * @return durumu ve gerekirse uyarı mesajını içeren harita
     */
    @PostMapping("/portfolio")
    public Map<String, String> savePortfolio(@RequestBody PortfolioState incoming) {
        List<String> invalid = structuralErrors(incoming);
        if (!invalid.isEmpty()) {
            return Map.of("status", "error", "message", String.join(" | ", invalid));
        }
        String indexName = null;
        if (incoming.selectedIndex != null && bistIndices.containsIndex(incoming.selectedIndex)) {
            indexName = incoming.selectedIndex.toUpperCase();
        }
        portfolioService.updatePortfolio(
                incoming.budget,
                incoming.advisorMode,
                incoming.modelType,
                incoming.analysisType != null ? AnalysisType.fromKey(incoming.analysisType).name() : null,
                indexName,
                incoming.positions);
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
     * Portföy taslağının yapısal doğrulama hatalarını toplar.
     * <p>
     * Kurallar: bütçe sonlu ve ≥ 0 olmalı; pozisyonda sembol boş olamaz ve
     * {@code [A-Z0-9]{1,15}} desenine uymalı; lot > 0 olmalı; ortalama maliyet sonlu
     * ve > 0 olmalı.
     *
     * @param incoming doğrulanacak portföy taslağı
     * @return hata mesajları listesi (hata yoksa boş)
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
     * Taze günlük analizi çalıştırıp sonucu döndürür.
     * <p>
     * REST uç noktası: {@code POST /api/analyze}.
     *
     * @return önerileri ve uyarıları içeren analiz sonucu
     */
    @PostMapping("/analyze")
    public AnalysisResult analyze() { return dailyAdvisor.analyze(); }

    /**
     * Tek bir işlemin REST gövdesindeki gösterimi.
     * <p>
     * Bileşenler: {@code symbol} işlem gören hisse sembolü; {@code action} işlem türü
     * ({@code AL}/{@code SAT}); {@code lots} işlem lot sayısı; {@code price} işlem
     * fiyatı (TL).
     *
     * @param symbol hisse sembolü
     * @param action işlem türü ({@code AL} veya {@code SAT})
     * @param lots   işlem lot sayısı
     * @param price  işlem fiyatı (TL)
     */
    public record ConfirmReq(String symbol, String action, int lots, double price) {}

    /**
     * Toplu işlemi uygular.
     * <p>
     * REST uç noktası: {@code POST /api/confirm}. Gövdedeki
     * {@code [{symbol, action, lots, price}]} listesi sırayla
     * {@link PortfolioService#applyTransaction} ile işlenir. Yanıt
     * {@code {"status":"ok"}} ile birlikte uygulanan ({@code applied}) ve reddedilen
     * ({@code failed}) işlem sayılarını içerir.
     *
     * @param reqs uygulanacak işlem listesi
     * @return uygulanan ve reddedilen işlem sayılarını içeren yanıt haritası
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
        Map<String, String> res = new HashMap<>();
        res.put("status", "ok");
        res.put("applied", String.valueOf(applied));
        res.put("failed", String.valueOf(failed));
        return res;
    }

    /**
     * Bekleyen uygulanabilir önerileri döndürür.
     * <p>
     * REST uç noktası: {@code GET /api/pending}. Taze bir analiz çalıştırılır ve yalnızca
     * uygulanabilir SAT ve AL önerileri harita listesine dönüştürülür; TUT önerileri elenir.
     *
     * @return öneri haritalarının listesi
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
     * Bir öneriyi API harita gösterimine dönüştürür.
     *
     * @param r dönüştürülecek öneri
     * @return {@code index}, {@code symbol}, {@code action}, {@code lots}, {@code price},
     *         {@code score} ve {@code note} anahtarlarını içeren harita
     */
    private Map<String, Object> toMap(Recommendation r) {
        Map<String, Object> m = new HashMap<>();
        m.put("index", r.index());
        m.put("symbol", r.symbol());
        m.put("action", r.action());
        m.put("lots", r.lots());
        m.put("price", r.price());
        m.put("score", r.score());
        m.put("note", r.note());
        return m;
    }
}
