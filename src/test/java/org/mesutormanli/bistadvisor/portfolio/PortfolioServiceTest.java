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
 * {@link PortfolioService} portföy muhasebesinin testleri: açık nakit modeli
 * (gerçekleşen kâr/zararın nakde yansıması), kısmi/tam satış, lot ve nakit
 * kontrollerinin reddedilmesi, manuel güncelleme mutabakatı ve
 * {@code state.yaml} yükleme.
 * <p>
 * Her test {@code @TempDir} altında kendi durum dosyasıyla çalışır; hiçbir test
 * ağa çıkmaz ve gerçek portföy dosyasına dokunmaz.
 */
class PortfolioServiceTest {

    @TempDir
    Path dir;

    /**
     * Geçici dizin altında yeni bir {@link PortfolioService} kurar.
     * <p>
     * {@code @TempDir} altında {@code cache} ve {@code state.yaml} yolları
     * belirlenir; {@link AppConfig} alanları {@code ReflectionTestUtils} ile
     * doldurularak servis gerçek proje dizinlerinden izole edilir. Servis
     * kurucuda durumu diskten yüklediği için önceden yazılmış {@code state.yaml}
     * varsa aynen okunur.
     *
     * @return geçici dizine bağlı yeni bir {@link PortfolioService}
     */
    private PortfolioService newService() {
        AppConfig cfg = new AppConfig();
        ReflectionTestUtils.setField(cfg, "cacheDir", dir.resolve("cache").toString());
        ReflectionTestUtils.setField(cfg, "stateFile", dir.resolve("state.yaml").toString());
        return new PortfolioService(cfg);
    }

    /**
     * Portföyün nakit sermayeden kurulması ve pozisyon maliyetinin düşülmesi.
     * <p>
     * Senaryo: 50.000 TL bütçeyle 100×240 TL'lik THYAO (24.000 TL) ve 50×351 TL'lik
     * ASELS (17.550 TL) pozisyonları tanımlanır. Beklenen davranış: yatırılan
     * maliyet 41.550 TL olur, geriye kalan 8.450 TL nakit olarak yazılır ve
     * doğrulama uyarısız ({@code null}) geçer.
     */
    @Test
    void initPortfolioNakdiSermayedenKurar() {
        PortfolioService svc = newService();

        svc.initPortfolio(50_000, "BALANCED", "RANDOM_FOREST",
                List.of(new Position("THYAO", 100, 240), new Position("ASELS", 50, 351)));

        assertEquals(8_450, svc.availableCash(), 0.01);
        assertEquals(41_550, svc.investedCost(), 0.01);
        assertNull(svc.validatePortfolio());
    }

    /**
     * Kısmi satışta kalan lot ve gerçekleşen kâr nakde yansır.
     * <p>
     * Senaryo: 100.000 TL nakitle 100 lot THYAO 240 TL'den alınır (nakit 76.000 TL)
     * ve bunun 40 lotu 250 TL'den satılır. Beklenen davranış: pozisyon 60 lotta ve
     * 240 TL ortalama maliyetle (maliyet sabit kalır) küçülür; 40×(250−240)=400 TL
     * gerçekleşen kâr dahil nakit 86.000 TL'ye çıkar, nakit+yatırım toplamı
     * 100.400 TL olur, bütçe 100.000 TL'de kalır ve doğrulama uyarısız geçer.
     */
    @Test
    void kismiSatisPozisyonuKuculturVeGerceklesmisKarNakdeYansir() {
        PortfolioService svc = newService();
        svc.initPortfolio(100_000, "BALANCED", "RANDOM_FOREST", List.of());

        assertTrue(svc.applyTransaction("thyao", "AL", 100, 240));
        assertEquals(76_000, svc.availableCash(), 0.01);

        assertTrue(svc.applyTransaction("THYAO", "SAT", 40, 250));

        PortfolioState s = svc.getState();
        assertEquals(1, s.positions.size());
        assertEquals("THYAO", s.positions.get(0).symbol());
        assertEquals(60, s.positions.get(0).lots());
        assertEquals(240, s.positions.get(0).avgCost(), 0.01);
        assertEquals(86_000, svc.availableCash(), 0.01);
        assertEquals(100_400, svc.availableCash() + svc.investedCost(), 0.01);
        assertEquals(100_000, s.budget, 0.01);
        assertNull(svc.validatePortfolio());
    }

    /**
     * Tam satımda pozisyonun kalkması ve kârın nakde girmesi.
     * <p>
     * Senaryo: 100 lot THYAO 240 TL'den alınıp tamamı 250 TL'den satılır.
     * Beklenen davranış: pozisyon listesi boşalır, 1.000 TL kârla nakit 101.000 TL
     * olur ve bütçe 100.000 TL'de sabit kalır.
     */
    @Test
    void tamSatisPozisyonuKaldirirVeKariYansitir() {
        PortfolioService svc = newService();
        svc.initPortfolio(100_000, "BALANCED", "RANDOM_FOREST", List.of());
        assertTrue(svc.applyTransaction("THYAO", "AL", 100, 240));

        assertTrue(svc.applyTransaction("THYAO", "SAT", 100, 250));

        assertTrue(svc.getState().positions.isEmpty());
        assertEquals(101_000, svc.availableCash(), 0.01);
        assertEquals(100_000, svc.getState().budget, 0.01);
    }

    /**
     * Zararlı satımda nakdin azalması.
     * <p>
     * Senaryo: 100 lot THYAO 240 TL'den alınıp 200 TL'den satılır. Beklenen
     * davranış: 4.000 TL gerçekleşen zarar nakitten düşer ve nakit 96.000 TL'ye
     * iner.
     */
    @Test
    void zararliSatisNakdiAzaltir() {
        PortfolioService svc = newService();
        svc.initPortfolio(100_000, "BALANCED", "RANDOM_FOREST", List.of());
        assertTrue(svc.applyTransaction("THYAO", "AL", 100, 240));

        assertTrue(svc.applyTransaction("THYAO", "SAT", 100, 200));

        assertEquals(96_000, svc.availableCash(), 0.01);
    }

    /**
     * Elde olmayan lotun satılmasının reddedilmesi.
     * <p>
     * Senaryo: 100 lotu varken 150 lot THYAO satışı istenir. Beklenen davranış:
     * işlem {@code false} ile reddedilir ve pozisyon 100 lot olarak aynen kalır.
     */
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

    /**
     * Nakit üstü alımın reddedilip karşılanabilen kısmi alımın kabul edilmesi.
     * <p>
     * Senaryo: 100.000 TL nakitle önce 400×240=96.000 TL'lik alım yapılır; kalan
     * 4.000 TL'ye 100 lotluk alım (24.000 TL) sığmaz, 10 lotluk alım (2.400 TL) sığar.
     * Beklenen davranış: büyük alım {@code false} ile reddedilir, 10 lotluk alım
     * kabul edilir, nakit 1.600 TL olarak kalır ve doğrulama uyarısız geçer.
     */
    @Test
    void nakitUzerindeAlimReddedilir() {
        PortfolioService svc = newService();
        svc.initPortfolio(100_000, "BALANCED", "RANDOM_FOREST", List.of());

        assertTrue(svc.applyTransaction("THYAO", "AL", 400, 240));
        assertFalse(svc.applyTransaction("THYAO", "AL", 100, 240));
        assertTrue(svc.applyTransaction("THYAO", "AL", 10, 240));

        assertEquals(1_600, svc.availableCash(), 0.01);
        assertNull(svc.validatePortfolio());
    }

    /**
     * Negatif nakdin doğrulama uyarısıyla bildirilmesi.
     * <p>
     * Senaryo: 1.000 TL bütçeyle maliyeti 2.000 TL olan 10 lot THYAO (200 TL)
     * yüklenir. Beklenen davranış: nakit −1.000 TL'ye iner ve
     * {@code validatePortfolio} {@code "nakit"} kelimesini içeren bir uyarı
     * döndürür.
     */
    @Test
    void negatifNakitDogrulamaylaBildirilir() {
        PortfolioService svc = newService();
        svc.initPortfolio(1_000, "BALANCED", "RANDOM_FOREST",
                List.of(new Position("THYAO", 10, 200)));

        String warning = svc.validatePortfolio();
        assertNotNull(warning);
        assertTrue(warning.contains("nakit"), "uyarı nakit sorununu içermeli: " + warning);
        assertEquals(-1_000, svc.availableCash(), 0.01);
    }

    /**
     * Bütçe değişikliğinin fark kadar nakde işlenmesi.
     * <p>
     * Senaryo: 100.000 TL nakitli portföyde bütçe önce 110.000 TL'ye, sonra
     * 105.000 TL'ye güncellenir. Beklenen davranış: her artış/azalış doğrudan nakde
     * yansıtılır; nakit sırayla 110.000 TL ve 105.000 TL olur.
     */
    @Test
    void updatePortfolioButceDegisikliginiNakdeIsler() {
        PortfolioService svc = newService();
        svc.initPortfolio(100_000, "BALANCED", "RANDOM_FOREST", List.of());
        assertEquals(100_000, svc.availableCash(), 0.01);

        svc.updatePortfolio(110_000, null, null, null, null);
        assertEquals(110_000, svc.availableCash(), 0.01);

        svc.updatePortfolio(105_000, null, null, null, null);
        assertEquals(105_000, svc.availableCash(), 0.01);
    }

    /**
     * Pozisyon değişikliğinin maliyet farkı kadar nakde işlenmesi.
     * <p>
     * Senaryo: boş portföye 10 lot THYAO 240 TL'lik pozisyon eklenir, ardından
     * pozisyon listesi tekrar boşaltılır. Beklenen davranış: ekleme nakitten maliyet
     * kadar (−2.400 TL) götürür (nakit 97.600 TL), çıkarma aynı farkı geri verir
     * (+2.400 TL, nakit 100.000 TL).
     */
    @Test
    void updatePortfolioPozisyonFarkiniMaliyetKadarNakdeIsler() {
        PortfolioService svc = newService();
        svc.initPortfolio(100_000, "BALANCED", "RANDOM_FOREST", List.of());

        svc.updatePortfolio(0, null, null, null, List.of(new Position("THYAO", 10, 240)));
        assertEquals(97_600, svc.availableCash(), 0.01);

        svc.updatePortfolio(0, null, null, null, List.of());
        assertEquals(100_000, svc.availableCash(), 0.01);
    }

    /**
     * {@code state.yaml} alanlarının aynen yüklenmesi.
     * <p>
     * Senaryo: geçici dizine bütçe 1.000, nakit 500 ve 10 lot THYAO 90 TL maliyetli
     * pozisyon içeren YAML metin bloğu yazılır. Beklenen davranış: yeni servis bu
     * durumu kurucuda aynen okur; nakit 500 TL ve bütçe 1.000 TL olduğu gibi
     * görünür.
     *
     * @throws IOException test YAML dosyası yazılamazsa
     */
    @Test
    void yamlDosyasiAlanlariAynenYuklenir() throws IOException {
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