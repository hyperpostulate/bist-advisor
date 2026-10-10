package org.mesutormanli.bistadvisor.cli.commands;

import org.mesutormanli.bistadvisor.application.AnalysisResult;
import org.mesutormanli.bistadvisor.application.AnalysisService;
import org.mesutormanli.bistadvisor.application.PortfolioService;
import org.mesutormanli.bistadvisor.cli.ConsoleReportRenderer;
import org.springframework.stereotype.Component;

/**
 * {@code run} komutu: taze analizi çalıştırıp "Günlük Öneri" raporunu yazar.
 * <p>
 * Analiz {@link AnalysisService#analyze()} ile uçtan uca yürütülür; rapor
 * mevcut pozisyonların ortalama maliyetleriyle birlikte
 * {@link ConsoleReportRenderer#renderReport} ile yazılır.
 */
@Component
public class RunCommand implements CliCommand {

    private final AnalysisService analysisService;
    private final PortfolioService portfolioService;
    private final ConsoleReportRenderer renderer;

    /**
     * Kurar: analiz/portföy servislerini ve konsol raporlayıcısını enjekte eder.
     *
     * @param analysisService  günlük analiz servisi
     * @param portfolioService portföy uygulama servisi
     * @param renderer         konsol çıktı biçimi
     */
    public RunCommand(AnalysisService analysisService, PortfolioService portfolioService,
                      ConsoleReportRenderer renderer) {
        this.analysisService = analysisService;
        this.portfolioService = portfolioService;
        this.renderer = renderer;
    }

    /**
     * Komutun adını döndürür.
     *
     * @return {@code run}
     */
    @Override
    public String name() {
        return "run";
    }

    /**
     * {@code run} komutunu uygular: taze analizi çalıştırıp raporu yazar.
     *
     * @param args komut satırı argümanları (kullanılmaz)
     */
    @Override
    public void execute(String[] args) {
        AnalysisResult r = analysisService.analyze();
        renderer.renderReport(r, portfolioService.positions());
    }
}