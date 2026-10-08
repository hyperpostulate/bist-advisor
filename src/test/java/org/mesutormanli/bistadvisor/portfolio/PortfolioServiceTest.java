package org.mesutormanli.bistadvisor.portfolio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mesutormanli.bistadvisor.config.AppConfig;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PortfolioService} portföy muhasebesinin birim testleri: açık nakit modeli
 * (gerçekleşen kâr/zararın nakde yansıması), kısmi/tam satış,
 * nakit kontrolü, manuel güncelleme mutabakatı ve {@code state.yaml} yükleme.
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
    void initPortfolioNakdiSermayedenKurar() {
        PortfolioService svc = newService();

        svc.initPortfolio(50_000, "BALANCED", "RANDOM_FOREST",
                List.of(new Position("THYAO", 100, 240), new Position("ASELS", 50, 351)));

        // 24.000 + 17.550 = 41.550 maliyet → nakit 8.450
        assertEquals(8_450, svc.availableCash(), 0.01);
        assertEquals(41_550, svc.investedCost(), 0.01);
        assertNull(svc.validatePortfolio());
    }

    @Test
    void kismiSatisPozisyonuKuculturVeGerceklesmisKarNakdeYansir() {
        PortfolioService svc = newService();
        svc.initPortfolio(100_000, "BALANCED", "RANDOM_FOREST", List.of());

        assertTrue(svc.applyTransaction("thyao", "AL", 100, 240));
        assertEquals(76_000, svc.availableCash(), 0.01);

        // 40 lot @ 250 satılır: 10.000 tutar nakde döner, 400 TL kâr gerçekleşir
        assertTrue(svc.applyTransaction("THYAO", "SAT", 40, 250));

        PortfolioState s = svc.getState();
        assertEquals(1, s.positions.size());
        assertEquals("THYAO", s.positions.get(0).symbol());
        assertEquals(60, s.positions.get(0).lots());
        assertEquals(240, s.positions.get(0).avgCost(), 0.01);
        assertEquals(86_000, svc.availableCash(), 0.01);          // 76.000 + 10.000
        assertEquals(100_400, svc.availableCash() + svc.investedCost(), 0.01); // +400 kâr
        assertEquals(100_000, s.budget, 0.01);                     // sermaye katkısı sabit
        assertNull(svc.validatePortfolio());
    }

    @Test
    void tamSatisPozisyonuKaldirirVeKariYansitir() {
        PortfolioService svc = newService();
        svc.initPortfolio(100_000, "BALANCED", "RANDOM_FOREST", List.of());
        assertTrue(svc.applyTransaction("THYAO", "AL", 100, 240));

        assertTrue(svc.applyTransaction("THYAO", "SAT", 100, 250));

        assertTrue(svc.getState().positions.isEmpty());
        // 240'tan alıp 250'den satmak 1.000 TL kâr bırakmalı
        assertEquals(101_000, svc.availableCash(), 0.01);
        assertEquals(100_000, svc.getState().budget, 0.01);
    }

    @Test
    void zararliSatisNakdiAzaltir() {
        PortfolioService svc = newService();
        svc.initPortfolio(100_000, "BALANCED", "RANDOM_FOREST", List.of());
        assertTrue(svc.applyTransaction("THYAO", "AL", 100, 240));

        assertTrue(svc.applyTransaction("THYAO", "SAT", 100, 200));

        // 24.000 maliyetle alınıp 20.000'e satıldı → 4.000 TL zarar nakde işlendi
        assertEquals(96_000, svc.availableCash(), 0.01);
    }

    @Test
    void fazlaSatisReddedilir() {
        PortfolioService svc = newService();
        svc.initPortfolio(100_000, "BALANCED", "RANDOM_FOREST", List.of());
        assertTrue(svc.applyTransaction("THYAO", "AL", 100, 240));

        assertFalse(svc.applyTransaction("THYAO", "SAT", 150, 250));

        PortfolioState s = svc.getState();
        assertEquals(1, s.positions.size());
        assertEquals(100, s.positions.get(0).lots());
    }

    @Test
    void nakitUzerindeAlimReddedilir() {
        PortfolioService svc = newService();
        svc.initPortfolio(100_000, "BALANCED", "RANDOM_FOREST", List.of());

        assertTrue(svc.applyTransaction("THYAO", "AL", 400, 240));   // 96.000
        assertFalse(svc.applyTransaction("THYAO", "AL", 100, 240));  // 24.000 > 4.000 nakit
        assertTrue(svc.applyTransaction("THYAO", "AL", 10, 240));    // 2.400 ≤ 4.000 nakit

        assertEquals(1_600, svc.availableCash(), 0.01);
        assertNull(svc.validatePortfolio());
    }

    @Test
    void negatifNakitDogrulamaylaBildirilir() {
        PortfolioService svc = newService();
        // maliyeti (2.000) sermayesini (1.000) aşan portföy → negatif nakit
        svc.initPortfolio(1_000, "BALANCED", "RANDOM_FOREST",
                List.of(new Position("THYAO", 10, 200)));

        String warning = svc.validatePortfolio();
        assertNotNull(warning);
        assertTrue(warning.contains("nakit"), "uyarı nakit sorununu içermeli: " + warning);
        assertEquals(-1_000, svc.availableCash(), 0.01);
    }

    @Test
    void updatePortfolioButceDegisikliginiNakdeIsler() {
        PortfolioService svc = newService();
        svc.initPortfolio(100_000, "BALANCED", "RANDOM_FOREST", List.of());
        assertEquals(100_000, svc.availableCash(), 0.01);

        // para yatırma: +10.000 nakde işlenir
        svc.updatePortfolio(110_000, null, null, null, null);
        assertEquals(110_000, svc.availableCash(), 0.01);

        // para çekme: −5.000 nakde işlenir
        svc.updatePortfolio(105_000, null, null, null, null);
        assertEquals(105_000, svc.availableCash(), 0.01);
    }

    @Test
    void updatePortfolioPozisyonFarkiniMaliyetKadarNakdeIsler() {
        PortfolioService svc = newService();
        svc.initPortfolio(100_000, "BALANCED", "RANDOM_FOREST", List.of());

        svc.updatePortfolio(0, null, null, null, List.of(new Position("THYAO", 10, 240)));
        assertEquals(97_600, svc.availableCash(), 0.01);

        // pozisyon kaydı silinince maliyet bedeli nakde geri döner
        svc.updatePortfolio(0, null, null, null, List.of());
        assertEquals(100_000, svc.availableCash(), 0.01);
    }

    @Test
    void yamlDosyasiAlanlariAynenYuklenir() throws IOException {
        // 1.000 TL sermaye katkısı + 400 TL gerçekleşen kâr (nakit 500 + maliyet 900 = 1.400)
        Files.writeString(dir.resolve("state.yaml"), """
                budget: 1000.0
                cash: 500.0
                positions:
                - symbol: "THYAO"
                  lots: 10
                  avgCost: 90.0
                """);

        PortfolioService svc = newService();

        assertEquals(500, svc.availableCash(), 0.01);
        assertEquals(1_000, svc.getState().budget, 0.01);
    }
}