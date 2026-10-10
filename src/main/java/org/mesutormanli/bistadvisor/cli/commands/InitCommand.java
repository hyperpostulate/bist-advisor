package org.mesutormanli.bistadvisor.cli.commands;

import org.mesutormanli.bistadvisor.application.PortfolioService;
import org.mesutormanli.bistadvisor.cli.ConsoleReportRenderer;
import org.mesutormanli.bistadvisor.cli.InitArgs;
import org.mesutormanli.bistadvisor.domain.advisor.RiskProfile;
import org.mesutormanli.bistadvisor.domain.portfolio.ValidationResult;
import org.mesutormanli.bistadvisor.domain.settings.AnalysisType;
import org.mesutormanli.bistadvisor.domain.settings.ModelType;
import org.springframework.stereotype.Component;

/**
 * {@code init} komutu: portföyü bütçe ve parametrelerle başlatır.
 * <p>
 * Verilmeyen mod/model/analiz tipi mevcut portföyden miras alınır. Önce
 * {@link PortfolioService#initialize} çağrılır; doğrulama uyarısı varsa
 * "Uyari:" olarak basılır, ardından sermaye/nakit/mod/model/analiz tipi özet satırı
 * yazılır. Ayrıştırma hataları "Hatalar:" listesinde basılır, geçerli kısımlarla
 * başlatma yine de yapılır.
 */
@Component
public class InitCommand implements CliCommand {

    private final PortfolioService portfolioService;
    private final ConsoleReportRenderer renderer;

    /**
     * Kurar: portföy servisini ve konsol raporlayıcısını enjekte eder.
     *
     * @param portfolioService portföy uygulama servisi
     * @param renderer         konsol çıktı biçimi
     */
    public InitCommand(PortfolioService portfolioService, ConsoleReportRenderer renderer) {
        this.portfolioService = portfolioService;
        this.renderer = renderer;
    }

    /**
     * Komutun adını döndürür.
     *
     * @return {@code init}
     */
    @Override
    public String name() {
        return "init";
    }

    /**
     * {@code init} komutunu uygular.
     *
     * @param args {@code init} komutu ve argümanları
     */
    @Override
    public void execute(String[] args) {
        InitArgs init = InitArgs.parse(args);
        if (!init.errors().isEmpty()) {
            renderer.renderErrors(init.errors());
        }
        init(init.budget(), init.mode(), init.model(), init.analysis(), init.positions());
    }

    /**
     * Portföyü başlatır, uyarıyı ve özet satırını yazar.
     *
     * @param budget   başlangıç sermayesi (TL)
     * @param mode     danışman modu etiketi (null ise mevcut mod miras alınır)
     * @param model    model anahtarı (null ise mevcut model miras alınır)
     * @param analysis analiz tipi anahtarı (null ise mevcut analiz tipi miras alınır)
     * @param positions açılacak pozisyon listesi
     */
    private void init(double budget, String mode, String model, String analysis,
                      java.util.List<org.mesutormanli.bistadvisor.domain.portfolio.Position> positions) {
        String modeName = mode != null
                ? RiskProfile.fromLabel(mode).name() : portfolioService.riskProfile().name();
        String modelName = model != null
                ? ModelType.fromKey(model).name() : portfolioService.modelType().name();
        String analysisName = analysis != null
                ? AnalysisType.fromKey(analysis).name() : portfolioService.analysisType().name();
        portfolioService.initialize(budget, modeName, modelName, analysisName, positions);
        ValidationResult vr = portfolioService.validate();
        if (!vr.ok()) {
            renderer.renderWarning(vr.message());
        }
        renderer.renderInitSummary(budget, portfolioService.availableCash(),
                modeName, modelName, analysisName);
    }
}