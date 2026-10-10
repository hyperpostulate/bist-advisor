package org.mesutormanli.bistadvisor.adapters.cache;

import org.junit.jupiter.api.Test;
import org.mesutormanli.bistadvisor.domain.market.Bar;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link BarCsvCodec} saf CSV dönüşümünün testleri: kodlama biçimi, hem 3 kolonlu
 * {@code tarih,kapanış,hacim} hem de OHLCV çözümlenmesi, bozuk satırların
 * sessizce atlanması.
 * <p>
 * Codec dönüştürme dışında hiçbir iş yapmaz; testler girdi/çıktı eşitliğine bakar.
 */
class BarCsvCodecTest {

    /**
     * Kodlamanın {@code tarih,kapanış,hacim} biçimini birebir üretmesi.
     * <p>
     * Senaryo: hacmi kesirli bir bar kodlanır. Beklenen davranış: hacim tam sayıya
     * yuvarlanır, kapanış kayan nokta biçimiyle korunur ve satır
     * {@code "2026-01-02,241.5,900"} olur.
     */
    @Test
    void encodeBiçimiBirebirUretir() {
        assertEquals("2026-01-02,241.5,900",
                BarCsvCodec.encode(new Bar("2026-01-02", 241.5, 900.7)));
        assertEquals(List.of("2026-01-01,240.0,1000"),
                BarCsvCodec.encode(List.of(new Bar("2026-01-01", 240, 1000))));
    }

    /**
     * 3 kolonlu {@code tarih,kapanış,hacim} satırlarının çözümlenmesi.
     * <p>
     * Senaryo: iki satırlık basit CSV beslenir. Beklenen davranış: kapanış 2.,
     * hacim 3. alandan okunur ve barlar verilen sırayla döner.
     */
    @Test
    void ucKolonluSatirlarCozumlenir() {
        List<Bar> bars = BarCsvCodec.decode(
                List.of("2026-01-01,240.0,1000", "2026-01-02,241.0,900"));

        assertEquals(List.of(new Bar("2026-01-01", 240, 1000),
                new Bar("2026-01-02", 241, 900)), bars);
    }

    /**
     * OHLCV satırlarından kapanış ve hacmin doğru kolonlardan okunması.
     * <p>
     * Senaryo: 6 kolonlu OHLCV ve 5 kolonlu (hacimsiz) satırlar beslenir.
     * Beklenen davranış: 6 kolonda kapanış 5. (indeks 4), hacim 6. (indeks 5)
     * alandan; 5 kolonda kapanış 5. alandan, hacim 3. alandan (indeks 2) okunur.
     */
    @Test
    void ohlcvSatirlariDogruKolonlardanCozumlenir() {
        List<Bar> bars = BarCsvCodec.decode(List.of(
                "2026-01-01,239,241,238,240.5,1200",
                "2026-01-02,239,241,238,241.5"));

        assertEquals(2, bars.size());
        assertEquals(new Bar("2026-01-01", 240.5, 1200), bars.get(0));
        assertEquals(new Bar("2026-01-02", 241.5, 241), bars.get(1));
    }

    /**
     * Eksik kolonlu ve sayısal olmayan satırların sessizce atlanması.
     * <p>
     * Senaryo: 2 kolonlu, boş ve bozuk sayı içeren satırlar beslenir. Beklenen
     * davranış: bu satırlar istisna üretmeden atlanır, yalnız geçerli satır
     * barda döner.
     */
    @Test
    void bozukSatirlarSessizceAtlanir() {
        List<Bar> bars = BarCsvCodec.decode(List.of(
                "eksik", "", "2026-01-01,metin,1000", "2026-01-02,241.0,900"));

        assertEquals(List.of(new Bar("2026-01-02", 241, 900)), bars);
    }
}