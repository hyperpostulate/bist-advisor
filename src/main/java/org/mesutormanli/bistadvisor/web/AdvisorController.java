package org.mesutormanli.bistadvisor.web;

import org.mesutormanli.bistadvisor.adapters.indices.BistIndices;
import org.mesutormanli.bistadvisor.application.AnalysisResult;
import org.mesutormanli.bistadvisor.application.AnalysisService;
import org.mesutormanli.bistadvisor.application.PortfolioService;
import org.mesutormanli.bistadvisor.application.PortfolioViewService;
import org.mesutormanli.bistadvisor.domain.advisor.RiskProfile;
import org.mesutormanli.bistadvisor.domain.advisor.Signal;
import org.mesutormanli.bistadvisor.domain.portfolio.PortfolioSnapshot;
import org.mesutormanli.bistadvisor.domain.portfolio.PortfolioValidator;
import org.mesutormanli.bistadvisor.domain.portfolio.Position;
import org.mesutormanli.bistadvisor.domain.portfolio.PositionDraft;
import org.mesutormanli.bistadvisor.domain.portfolio.ValidationResult;
import org.mesutormanli.bistadvisor.domain.settings.AnalysisType;
import org.mesutormanli.bistadvisor.domain.settings.ModelType;
import org.mesutormanli.bistadvisor.web.dto.AnalysisResponse;
import org.mesutormanli.bistadvisor.web.dto.ConfigDto;
import org.mesutormanli.bistadvisor.web.dto.ConfirmResponse;
import org.mesutormanli.bistadvisor.web.dto.PortfolioDraft;
import org.mesutormanli.bistadvisor.web.dto.PortfolioViewDto;
import org.mesutormanli.bistadvisor.web.dto.RecommendationDto;
import org.mesutormanli.bistadvisor.web.dto.SavePortfolioResponse;
import org.mesutormanli.bistadvisor.web.dto.TransactionDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * REST API'nin ince HTTP katmanı: istek gövdelerini tip güvenli DTO'larla
 * karşılar, işi uygulama servislerine devreder ve yanıtları API gövdelerine
 * çevirir.
 * <p>
 * Uç noktalar: {@code GET /api/config} yapılandırma, {@code GET/POST /api/portfolio}
 * durum okuma/kaydetme, {@code GET /api/portfolio-view} kâr/zarar raporu,
 * {@code POST /api/analyze} günlük analiz, {@code POST /api/confirm} işlem
 * uygulama ve {@code GET /api/pending} bekleyen öneriler. Taslak biçimi
 * kontrolleri {@link PortfolioValidator} ile burada, kurallar portföy
 * servisinde yürütülür.
 */
@RestController
@RequestMapping("/api")
public class AdvisorController {

    private final PortfolioService portfolioService;
    private final AnalysisService analysisService;
    private final PortfolioViewService portfolioViewService;
    private final BistIndices bistIndices;

    /**
     * Kurar: uygulama servislerini ve endeks kataloğunu enjekte eder.
     *
     * @param portfolioService     portföy uygulama servisi
     * @param analysisService      günlük analiz servisi
     * @param portfolioViewService portföy rapor servisi
     * @param bistIndices          endeks kataloğu
     */
    public AdvisorController(PortfolioService portfolioService, AnalysisService analysisService,
                             PortfolioViewService portfolioViewService, BistIndices bistIndices) {
        this.portfolioService = portfolioService;
        this.analysisService = analysisService;
        this.portfolioViewService = portfolioViewService;
        this.bistIndices = bistIndices;
    }

    /**
     * Arayüzlerin seçim listelerini ve o anki seçimleri döndürür.
     *
     * @return yapılandırma gövdesi
     */
    @GetMapping("/config")
    public ConfigDto config() {
        List<String> modes = new ArrayList<>();
        for (RiskProfile m : RiskProfile.values()) modes.add(m.name());
        List<String> models = new ArrayList<>();
        for (ModelType m : ModelType.values()) models.add(m.name());
        List<String> analysisTypes = new ArrayList<>();
        for (AnalysisType a : AnalysisType.values()) analysisTypes.add(a.name());
        return new ConfigDto(modes, models, analysisTypes, bistIndices.indexNames(),
                portfolioService.riskProfile().name(),
                portfolioService.modelType().name(),
                portfolioService.analysisType().name(),
                portfolioService.selectedIndex());
    }

    /**
     * Portföy durumunun anlık görüntüsünü döndürür.
     *
     * @return durum dosyasıyla aynı alanları taşıyan anlık görüntü
     */
    @GetMapping("/portfolio")
    public PortfolioSnapshot portfolio() {
        return portfolioService.snapshot();
    }

    /**
     * Portföyün kâr/zarar raporunu döndürür.
     *
     * @return pozisyon satırları ve toplamlar
     */
    @GetMapping("/portfolio-view")
    public PortfolioViewDto portfolioView() {
        return ApiMapper.toDto(portfolioViewService.view());
    }

    /**
     * Gelen portföy taslağını doğrular ve kaydeder.
     *
     * @param incoming portföy taslağı
     * @return taslak geçersizse {@code error}; kaydedilip kural ihlali varsa
     *         {@code warning}; aksi hâlde {@code ok}
     */
    @PostMapping("/portfolio")
    public SavePortfolioResponse savePortfolio(@RequestBody PortfolioDraft incoming) {
        ValidationResult structural = PortfolioValidator.structural(incoming.budget(), incoming.positions());
        if (!structural.ok()) {
            return new SavePortfolioResponse("error", structural.message());
        }
        String indexName = null;
        if (incoming.selectedIndex() != null && bistIndices.containsIndex(incoming.selectedIndex())) {
            indexName = incoming.selectedIndex().toUpperCase();
        }
        portfolioService.update(incoming.budget(), incoming.advisorMode(), incoming.modelType(),
                incoming.analysisType() != null ? AnalysisType.fromKey(incoming.analysisType()).name() : null,
                indexName, toPositions(incoming.positions()));
        ValidationResult rules = portfolioService.validate();
        return rules.ok()
                ? new SavePortfolioResponse("ok", null)
                : new SavePortfolioResponse("warning", rules.message());
    }

    /**
     * Doğrulanmış taslakları alan nesnelerine çevirir.
     *
     * @param drafts taslak pozisyonlar ({@code null} olabilir)
     * @return alan nesnesi pozisyonları ({@code null} kalemler atlanır)
     */
    private static List<Position> toPositions(List<PositionDraft> drafts) {
        if (drafts == null) return null;
        List<Position> positions = new ArrayList<>();
        for (PositionDraft p : drafts) {
            if (p != null) positions.add(new Position(p.symbol(), p.lots(), p.avgCost()));
        }
        return positions;
    }

    /**
     * Günlük analizi çalıştırır ve sonuçları döndürür.
     *
     * @return analiz gövdesi (öneriler, nakit, slotlar, uyarılar)
     */
    @PostMapping("/analyze")
    public AnalysisResponse analyze() {
        return ApiMapper.toDto(analysisService.analyze());
    }

    /**
     * AL/SAT işlemlerini uygular ve özet döndürür.
     *
     * @param reqs uygulanacak işlemler
     * @return uygulanan/reddedilen sayaçları
     */
    @PostMapping("/confirm")
    public ConfirmResponse confirm(@RequestBody List<TransactionDto> reqs) {
        int applied = 0;
        int failed = 0;
        for (TransactionDto r : reqs) {
            if (portfolioService.apply(r.symbol(), r.action(), r.lots(), r.price())) {
                applied++;
            } else {
                failed++;
            }
        }
        return new ConfirmResponse("ok", applied, failed);
    }

    /**
     * Bekleyen (uygulanmaya hazır) önerileri döndürür: pozisyonlardaki SAT ve
     * alım listesindeki AL sinyalleri.
     *
     * @return uygulanabilir öneri kalemleri
     */
    @GetMapping("/pending")
    public List<RecommendationDto> pending() {
        AnalysisResult a = analysisService.analyze();
        List<RecommendationDto> out = new ArrayList<>();
        for (var r : a.holdings()) {
            if (r.action() == Signal.SAT) out.add(ApiMapper.toDto(r));
        }
        for (var r : a.buys()) {
            if (r.action() == Signal.AL) out.add(ApiMapper.toDto(r));
        }
        return out;
    }
}