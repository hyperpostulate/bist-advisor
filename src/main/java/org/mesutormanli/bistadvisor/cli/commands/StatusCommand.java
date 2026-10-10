package org.mesutormanli.bistadvisor.cli.commands;

import org.mesutormanli.bistadvisor.application.PortfolioService;
import org.mesutormanli.bistadvisor.cli.ConsoleReportRenderer;
import org.springframework.stereotype.Component;

/**
 * {@code status} komutu: portföy özetini konsola yazar.
 * <p>
 * Sermaye, nakit, mod ve modelin yanı sıra pozisyon listesi (sembol, lot,
 * ortalama maliyet) yazdırılır; en fazla {@code maxPositions} pozisyon gösterilir.
 */
@Component
public class StatusCommand implements CliCommand {

    private final PortfolioService portfolioService;
    private final ConsoleReportRenderer renderer;

    /**
     * Kurar: portföy servisini ve konsol raporlayıcısını enjekte eder.
     *
     * @param portfolioService portföy uygulama servisi
     * @param renderer         konsol çıktı biçimi
     */
    public StatusCommand(PortfolioService portfolioService, ConsoleReportRenderer renderer) {
        this.portfolioService = portfolioService;
        this.renderer = renderer;
    }

    /**
     * Komutun adını döndürür.
     *
     * @return {@code status}
     */
    @Override
    public String name() {
        return "status";
    }

    /**
     * {@code status} komutunu uygular: portföy özetini yazar.
     *
     * @param args komut satırı argümanları (kullanılmaz)
     */
    @Override
    public void execute(String[] args) {
        renderer.renderStatus(portfolioService.snapshot(), portfolioService.maxPositions());
    }
}