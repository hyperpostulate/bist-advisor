package org.mesutormanli.bistadvisor.cli.commands;

import org.mesutormanli.bistadvisor.application.PortfolioService;
import org.mesutormanli.bistadvisor.cli.ConfirmArgs;
import org.mesutormanli.bistadvisor.cli.ConsoleReportRenderer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code confirm} komutu: işlemler ayrıştırılıp portföye işlenir.
 * <p>
 * Her argüman {@code SEMBOL,AL/SAT,lot,fiyat} biçimindedir. 4 alandan az alan,
 * geçersiz AL/SAT veya sayısal hata "Hatalar:" listesinde toplanır (bkz.
 * {@link ConfirmArgs}). Başarısız uygulamalar da aynı listeye
 * {@code "Islem basarisiz: "} metniyle eklenir; sonunda uygulanan işlem sayısı
 * yazdırılır. Hata sıralaması eski sürüme sadık kalsın diye ayrıştırma ve
 * uygulama TEK geçişte yapılır.
 */
@Component
public class ConfirmCommand implements CliCommand {

    private final PortfolioService portfolioService;
    private final ConsoleReportRenderer renderer;

    /**
     * Kurar: portföy servisini ve konsol raporlayıcısını enjekte eder.
     *
     * @param portfolioService portföy uygulama servisi
     * @param renderer         konsol çıktı biçimi
     */
    public ConfirmCommand(PortfolioService portfolioService, ConsoleReportRenderer renderer) {
        this.portfolioService = portfolioService;
        this.renderer = renderer;
    }

    /**
     * Komutun adını döndürür.
     *
     * @return {@code confirm}
     */
    @Override
    public String name() {
        return "confirm";
    }

    /**
     * {@code confirm} komutunu uygular: işlem argümanlarını tek geçişte ayrıştırır
     * ve portföye işler.
     *
     * @param args {@code confirm} komutu ve onu izleyen işlem metinleri
     */
    @Override
    public void execute(String[] args) {
        List<String> errors = new ArrayList<>();
        int applied = 0;
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            ConfirmArgs tx = ConfirmArgs.parse(a);
            if (!tx.ok()) {
                errors.add(tx.error());
                continue;
            }
            if (portfolioService.apply(tx.symbol(), tx.action(), tx.lots(), tx.price())) {
                applied++;
            } else {
                errors.add("Islem basarisiz: " + a);
            }
        }
        renderer.renderConfirmSummary(applied, errors);
    }
}