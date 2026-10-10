package org.mesutormanli.bistadvisor.cli.commands;

import org.mesutormanli.bistadvisor.application.ModelTrainingService;
import org.mesutormanli.bistadvisor.application.PortfolioService;
import org.mesutormanli.bistadvisor.cli.ConsoleReportRenderer;
import org.mesutormanli.bistadvisor.domain.settings.AnalysisType;
import org.mesutormanli.bistadvisor.domain.settings.ModelType;
import org.springframework.stereotype.Component;

/**
 * {@code train} komutu: seçili modeli seçili endeks ve analiz tipi için eğitir.
 * <p>
 * {@link ModelTrainingService#train} çağrılır; model yalnızca bellekte tutulur.
 * Başlangıç ve bitiş satırları {@link ConsoleReportRenderer} ile yazılır.
 */
@Component
public class TrainCommand implements CliCommand {

    private final PortfolioService portfolioService;
    private final ModelTrainingService modelTrainingService;
    private final ConsoleReportRenderer renderer;

    /**
     * Kurar: portföy/model servislerini ve konsol raporlayıcısını enjekte eder.
     *
     * @param portfolioService    portföy uygulama servisi (seçimler için)
     * @param modelTrainingService model yaşam döngüsü servisi
     * @param renderer            konsol çıktı biçimi
     */
    public TrainCommand(PortfolioService portfolioService, ModelTrainingService modelTrainingService,
                        ConsoleReportRenderer renderer) {
        this.portfolioService = portfolioService;
        this.modelTrainingService = modelTrainingService;
        this.renderer = renderer;
    }

    /**
     * Komutun adını döndürür.
     *
     * @return {@code train}
     */
    @Override
    public String name() {
        return "train";
    }

    /**
     * {@code train} komutunu uygular: seçili modeli yeniden eğitir.
     *
     * @param args komut satırı argümanları (kullanılmaz)
     */
    @Override
    public void execute(String[] args) {
        ModelType t = portfolioService.modelType();
        AnalysisType a = portfolioService.analysisType();
        String idx = portfolioService.snapshot().selectedIndex;
        renderer.renderTrainStart(t, a, idx);
        modelTrainingService.train(t, a, idx);
        renderer.renderTrainDone(t.key, a.key);
    }
}