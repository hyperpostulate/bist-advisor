package org.mesutormanli.bistadvisor.portfolio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mesutormanli.bistadvisor.config.AppConfig;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PortfolioService} portföy muhasebesinin birim testleri: kısmi satış,
 * nakit türetimi, bütçe mutabakatı ve eski sürüm state dosyasından göç.
 * <p>
 * Hiçbir test ağa çıkmaz; state dosyası geçici dizine yazılır.
 */
class PortfolioServiceTest {

    @TempDir
    Path dir;

    private PortfolioService newService() {
        AppConfig cfg = new AppConfig();
        ReflectionTestUtils.setField(cfg, "cacheDir", dir.resolve("cache").toString());
        ReflectionTestUtils.setField(cfg, "stateFile", dir.resolve("state.yaml").toString());
        return new PortfolioService(cfg);
    }

    @Test
    void kismiSatisPozisyonuKuculturVeNakitTuretilir() {
        PortfolioService svc = newService();
        svc.updateState(s -> s.budget = 100_000);

        assertTrue(svc.applyTransaction("thyao", "AL", 100, 240));
        assertEquals(76_000, svc.availableCash(null), 0.01);

        assertTrue(svc.applyTransaction("THYAO", "SAT", 40, 250));

        PortfolioState s = svc.getState();
        assertEquals(1, s.positions.size());
        assertEquals("THYAO", s.positions.get(0).symbol());
        assertEquals(60, s.positions.get(0).lots());
        assertEquals(240, s.positions.get(0).avgCost(), 0.01);
        // 60 lot * 240 = 14.400 maliyet kaldı → nakit 85.600
        assertEquals(85_600, svc.availableCash(null), 0.01);
        // toplam sermaye işlemlerden etkilenmez
        assertEquals(100_000, s.budget, 0.01);
        assertNull(svc.validatePortfolio());
    }

    @Test
    void fazlaSatisReddedilir() {
        PortfolioService svc = newService();
        svc.updateState(s -> s.budget = 100_000);
        assertTrue(svc.applyTransaction("THYAO", "AL", 100, 240));

        assertFalse(svc.applyTransaction("THYAO", "SAT", 150, 250));

        PortfolioState s = svc.getState();
        assertEquals(1, s.positions.size());
        assertEquals(100, s.positions.get(0).lots());
    }

    @Test
    void tamSatisPozisyonuKaldirir() {
        PortfolioService svc = newService();
        svc.updateState(s -> s.budget = 100_000);
        assertTrue(svc.applyTransaction("THYAO", "AL", 100, 240));

        assertTrue(svc.applyTransaction("THYAO", "SAT", 100, 250));

        assertTrue(svc.getState().positions.isEmpty());
        assertEquals(100_000, svc.availableCash(null), 0.01);
    }

    @Test
    void nakitUzerindeAlimReddedilir() {
        PortfolioService svc = newService();
        svc.updateState(s -> s.budget = 100_000);

        assertTrue(svc.applyTransaction("THYAO", "AL", 400, 240));   // 96.000
        assertFalse(svc.applyTransaction("THYAO", "AL", 100, 240));  // 24.000 > 4.000 nakit
        assertTrue(svc.applyTransaction("THYAO", "AL", 10, 240));    // 2.400 ≤ 4.000 nakit

        assertEquals(1_600, svc.availableCash(null), 0.01);
        assertNull(svc.validatePortfolio());
    }

    @Test
    void butceAsimiDogrulamaylaBildirilir() {
        PortfolioService svc = newService();
        svc.updateState(s -> {
            s.budget = 1_000;
            s.positions.add(new Position("THYAO", 10, 200));   // maliyet 2.000 > sermaye 1.000
        });

        String warning = svc.validatePortfolio();
        assertNotNull(warning);
        assertTrue(warning.contains("asiyor"), "uyarı maliyet/sorunu içermeli: " + warning);
    }

    @Test
    void eskiSurumButceSemantigiToplamSermayeyeYukseltilir() throws IOException {
        // Eski sürümde "budget" alanı kalan nakitti (200 TL) ve 10 lot * 100 TL = 1.000 TL'lik
        // pozisyon tutuluyordu → gerçek toplam sermaye 1.200 TL olmalı.
        Files.writeString(dir.resolve("state.yaml"), """
                budget: 200.0
                positions:
                - symbol: "THYAO"
                  lots: 10
                  avgCost: 100.0
                """);

        PortfolioService svc = newService();

        assertEquals(200, svc.availableCash(null), 0.01);
        assertEquals(1_200, svc.getState().budget, 0.01);
        assertNull(svc.validatePortfolio());
    }
}