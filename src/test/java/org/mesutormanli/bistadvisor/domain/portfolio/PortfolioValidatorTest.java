package org.mesutormanli.bistadvisor.domain.portfolio;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PortfolioValidator} metin tabanlı doğrulama mesajlarının testleri:
 * taslak biçimi kontrolleri (bütçe, sembol, lot, maliyet) ve portföy kuralları
 * (negatif nakit, slot sayısı sınırı).
 * <p>
 * Mesajlar eski sürümlerle birebir aynıdır ve API katmanında
 * {@code SavePortfolioResponse.message} alanında kullanıcıya ulaşır.
 */
class PortfolioValidatorTest {

    /**
     * Geçersiz bütçenin eski mesajla bildirilmesi.
     * <p>
     * Senaryo: negatif bütçeyle boş taslak doğrulanır. Beklenen davranış:
     * {@code "Gecersiz butce: -5.0"} hatası üretilir ve sonuç geçersizdir.
     */
    @Test
    void gecersizButceHataMesajiUretir() {
        ValidationResult vr = PortfolioValidator.structural(-5, List.of());

        assertFalse(vr.ok());
        assertTrue(vr.errors().contains("Gecersiz butce: -5.0"), vr.errors().toString());
    }

    /**
     * Eksik/geçersiz sembol, lot ve maliyet mesajlarının aynen korunması.
     * <p>
     * Senaryo: sembolü boş, sembolü geçersiz karakterli, lotu sıfır ve maliyeti
     * sıfır taslaklar doğrulanır. Beklenen davranış: sırasıyla
     * {@code "Pozisyonda sembol eksik"}, {@code "Gecersiz sembol: thy@ao"},
     * {@code "THYAO: lot sayisi pozitif olmali (0)"} ve
     * {@code "THYAO: birim maliyet pozitif olmali (0.0)"} mesajları üretilir.
     */
    @Test
    void taslakBiçimMesajlariKorunur() {
        ValidationResult vr = PortfolioValidator.structural(50_000, List.of(
                new PositionDraft(null, 1, 1),
                new PositionDraft("thy@ao", 1, 1),
                new PositionDraft("THYAO", 0, 1),
                new PositionDraft("THYAO", 1, 0)));

        assertFalse(vr.ok());
        assertTrue(vr.errors().contains("Pozisyonda sembol eksik"));
        assertTrue(vr.errors().contains("Gecersiz sembol: thy@ao"));
        assertTrue(vr.errors().contains("THYAO: lot sayisi pozitif olmali (0)"));
        assertTrue(vr.errors().contains("THYAO: birim maliyet pozitif olmali (0.0)"));
    }

    /**
     * Mesajların {@code " | "} ile birleştirilmesi ve geçerli taslağın onaylanması.
     * <p>
     * Senaryo: iki hatalı taslak doğrulanıp {@code message()} okunur; ardından
     * geçerli bir taslak doğrulanır. Beklenen davranış: hata metinleri
     * {@code " | "} ile birleşir ve geçerli taslak {@code ValidationResult.OK}
     * ile sonuçlanır.
     */
    @Test
    void mesajlarBirlestirilirVeGecerliTaslakOnaylanir() {
        ValidationResult vr = PortfolioValidator.structural(-5,
                List.of(new PositionDraft("THYAO", 0, 1)));

        assertEquals("Gecersiz butce: -5.0 | THYAO: lot sayisi pozitif olmali (0)",
                vr.message());

        assertEquals(ValidationResult.OK,
                PortfolioValidator.structural(50_000,
                        List.of(new PositionDraft("THYAO", 10, 240))));
    }

    /**
     * Negatif nakit ve slot sayısı ihlalinin eski mesajlarla bildirilmesi.
     * <p>
     * Senaryo: nakdi eksiye düşen portföy (bütçe 1.000, maliyet 2.000) ve 6
     * pozisyonlu portföy taranır. Beklenen davranış:
     * {@code "Negatif nakit (-1000 TL)"} ve {@code "Pozisyon sayisi (6) siniri (5) asiyor"}
     * mesajları üretilir; sorunsuz portföyde {@code ok()} true döner.
     */
    @Test
    void kuralIhlalleriEskiMesajlarlaBildirilir() {
        Portfolio negativeCash = new Portfolio();
        negativeCash.initialize(1_000, List.of(new Position("THYAO", 10, 200)));
        ValidationResult cashResult = PortfolioValidator.invariants(negativeCash);
        assertTrue(cashResult.errors().contains("Negatif nakit (-1000 TL)"),
                cashResult.errors().toString());

        Portfolio overLimit = Portfolio.restore(0, 0, List.of(
                new Position("AAA", 1, 1), new Position("BBB", 1, 1),
                new Position("CCC", 1, 1), new Position("DDD", 1, 1),
                new Position("EEE", 1, 1), new Position("FFF", 1, 1)));
        ValidationResult limitResult = PortfolioValidator.invariants(overLimit);
        assertTrue(limitResult.errors().contains("Pozisyon sayisi (6) siniri (5) asiyor"),
                limitResult.errors().toString());

        Portfolio clean = new Portfolio();
        clean.initialize(50_000, List.of(new Position("THYAO", 10, 240)));
        assertTrue(PortfolioValidator.invariants(clean).ok());
    }
}