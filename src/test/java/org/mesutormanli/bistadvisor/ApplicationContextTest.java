package org.mesutormanli.bistadvisor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.mesutormanli.bistadvisor.adapters.cache.CsvPriceCache;
import org.mesutormanli.bistadvisor.adapters.state.YamlPortfolioStateStore;
import org.mesutormanli.bistadvisor.adapters.yahoo.YahooMarketDataSource;
import org.mesutormanli.bistadvisor.application.AnalysisService;
import org.mesutormanli.bistadvisor.application.PortfolioService;
import org.mesutormanli.bistadvisor.web.AdvisorController;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Spring bağlamının (bağımlılık enjeksiyonu telinin) duman testi.
 * <p>
 * Katmanlı yapının tüm bileşenleri ({@code domain} hariç hepsi Spring bileşeni)
 * tek bağlamda kurulabilmeli: port uygulamaları adaptörlere, servisler portlara
 * ve controller servislere enjekte edilebilmelidir. Test bağlamı web sunucusu
 * açmaz ({@code webEnvironment = MOCK}) ve ağa çıkmaz; portföy durumu yalnızca
 * okunur.
 */
@SpringBootTest
class ApplicationContextTest {

    @Autowired
    PortfolioService portfolioService;

    @Autowired
    AnalysisService analysisService;

    @Autowired
    AdvisorController advisorController;

    @Autowired
    CsvPriceCache csvPriceCache;

    @Autowired
    YamlPortfolioStateStore yamlPortfolioStateStore;

    @Autowired
    YahooMarketDataSource yahooMarketDataSource;

    /**
     * Bağlamın kurulması ve tüm katman bileşenlerinin enjekte edilmesi.
     * <p>
     * Senaryo: uygulama bağlamı tamamen başlatılır. Beklenen davranış: enjeksiyon
     * zinciri kırılmaz (çift yapıcı/eksik bean hataları çıkmaz) ve her bileşen
     * erişilebilir olur.
     */
    @Test
    void baglamKatmanBilesenleriyleKurulur() {
        assertNotNull(portfolioService);
        assertNotNull(analysisService);
        assertNotNull(advisorController);
        assertNotNull(csvPriceCache);
        assertNotNull(yamlPortfolioStateStore);
        assertNotNull(yahooMarketDataSource);
    }
}