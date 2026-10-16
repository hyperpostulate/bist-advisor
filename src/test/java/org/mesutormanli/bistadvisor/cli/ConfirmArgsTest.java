package org.mesutormanli.bistadvisor.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ConfirmArgs} işlem argümanı ayrıştırmasının testleri: geçerli girdinin
 * alanlara bölünmesi ve hata metinlerinin eski CLI çıktılarıyla birebir aynı
 * olması ({@code "Gecersiz format ..."}, {@code "Gecersiz islem (...)"},
 * {@code "Gecersiz sayi: ..."}).
 */
class ConfirmArgsTest {

    /**
     * Geçerli işlemin alanlara doğru bölünmesi.
     * <p>
     * Senaryo: {@code THYAO,AL,100,240.5} girdisi verilir. Beklenen davranış:
     * sembol, işlem (büyük harfle), lot ve fiyat alanları ayrılır, hata yoktur.
     */
    @Test
    void validInputSplitIntoFields() {
        ConfirmArgs tx = ConfirmArgs.parse("THYAO,AL,100,240.5");

        assertTrue(tx.ok());
        assertNull(tx.error());
        assertEquals("THYAO", tx.symbol());
        assertEquals("AL", tx.action());
        assertEquals(100, tx.lots());
        assertEquals(240.5, tx.price(), 1e-9);
    }

    /**
     * İşlem türünün büyük/küçük harfsiz eşleşmesi.
     * <p>
     * Senaryo: {@code THYAO,sat,40,250} girdisi verilir. Beklenen davranış:
     * işlem alanı büyük harfe çevrilerek {@code SAT} olarak çözümlenir.
     */
    @Test
    void actionResolvedCaseInsensitively() {
        ConfirmArgs tx = ConfirmArgs.parse("THYAO,sat,40,250");

        assertTrue(tx.ok());
        assertEquals("SAT", tx.action());
    }

    /**
     * Eksik alanlı girdide biçim hata metninin üretilmesi.
     * <p>
     * Senaryo: 2 alanlı bir girdi verilir. Beklenen davranış: hata
     * {@code "Gecersiz format (beklenen: SEMBOL,AL/SAT,lot,fiyat): THYAO,AL"}
     * metnine eşittir.
     */
    @Test
    void missingFieldProducesFormatError() {
        ConfirmArgs tx = ConfirmArgs.parse("THYAO,AL");

        assertFalse(tx.ok());
        assertEquals("Gecersiz format (beklenen: SEMBOL,AL/SAT,lot,fiyat): THYAO,AL", tx.error());
    }

    /**
     * Geçersiz işlem türünde eski hata metninin üretilmesi.
     * <p>
     * Senaryo: {@code BEKLE} işlem türüyle girdi verilir. Beklenen davranış:
     * hata {@code "Gecersiz islem (BEKLE): THYAO,BEKLE,1,2"} metnine eşittir.
     */
    @Test
    void invalidActionProducesError() {
        ConfirmArgs tx = ConfirmArgs.parse("THYAO,BEKLE,1,2");

        assertFalse(tx.ok());
        assertEquals("Gecersiz islem (BEKLE): THYAO,BEKLE,1,2", tx.error());
    }

    /**
     * Sayısal alan bozuksa eski hata metninin üretilmesi.
     * <p>
     * Senaryo: lot alanı sayısal olmayan girdi verilir. Beklenen davranış: hata
     * {@code "Gecersiz sayi: THYAO,AL,x,2"} metnine eşittir.
     */
    @Test
    void invalidNumberProducesError() {
        ConfirmArgs tx = ConfirmArgs.parse("THYAO,AL,x,2");

        assertFalse(tx.ok());
        assertEquals("Gecersiz sayi: THYAO,AL,x,2", tx.error());
    }
}