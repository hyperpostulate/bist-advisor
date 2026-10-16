package org.mesutormanli.bistadvisor.adapters.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mesutormanli.bistadvisor.domain.market.Bar;
import org.mesutormanli.bistadvisor.domain.market.MarketTime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CsvPriceCache} fiyat önbelleği sözlüğünün testleri: dosya varlığının
 * sembol başına tazelik kararıyla sonuçlanması, bozuk içeriğin bayat sayılması
 * ve yazma-okama turunun satırları birebir koruması.
 * <p>
 * Tazelik kuralı {@link MarketTime} ile birlikte okunmalıdır: son barın tarihi
 * bugünden en fazla 4 gün gerideyse ({@code FRESH_WINDOW_DAYS = 4}) seri tazedir.
 * Her test {@code @TempDir} altında kendi önbellek dizinini kullanır; diske ve
 * zamana bağlı tek unsur bugünün tarihidir.
 */
class CsvPriceCacheTest {

    @TempDir
    Path dir;

    /**
     * Geçici dizin altında boş bir {@link CsvPriceCache} kurar.
     *
     * @return geçici dizine bağlı, içi boş yeni bir {@link CsvPriceCache}
     */
    private CsvPriceCache newStore() {
        return new CsvPriceCache(dir.resolve("cache"));
    }

    /**
     * Önbellek dosyası olmayan sembolün bayat sayılması.
     * <p>
     * Senaryo: daha önce hiç yazılmamış {@code YOK} sembolü sorgulanır. Beklenen
     * davranış: fiyat dosyası bulunamadığı için {@code hasFresh} false döner.
     */
    @Test
    void symbolWithoutFileConsideredStale() {
        assertFalse(newStore().hasFresh("YOK"));
    }

    /**
     * Son barı tolerans penceresindeki serinin taze sayılması.
     * <p>
     * Senaryo: THYAO önbelleğine son barı 2 gün öncesine ait tek satır yazılır.
     * Beklenen davranış: son bar 4 günlük tolerans penceresinin içinde kaldığı
     * için {@code hasFresh} true döner.
     */
    @Test
    void freshIfLastBarWithinToleranceWindow() {
        CsvPriceCache store = newStore();
        LocalDate recent = MarketTime.today().minusDays(2);
        store.write("THYAO", List.of(new Bar(recent.toString(), 240.0, 1000)));

        assertTrue(store.hasFresh("THYAO"));
    }

    /**
     * Pencere dışındaki son barın bayat sayılması.
     * <p>
     * Senaryo: THYAO önbelleğindeki tek bar 10 günlüktür. Beklenen davranış: son
     * bar 4 günlük tolerans penceresini aştığı için {@code hasFresh} false döner.
     */
    @Test
    void oldBarConsideredStale() {
        CsvPriceCache store = newStore();
        store.write("THYAO",
                List.of(new Bar(MarketTime.today().minusDays(10).toString(), 240.0, 1000)));

        assertFalse(store.hasFresh("THYAO"));
    }

    /**
     * Tarihi çözümlenemeyen içeriğin bayat sayılması.
     * <p>
     * Senaryo: dosyaya tarih alanı içermeyen tek bozuk satır yazılır. Beklenen
     * davranış: {@code DateTimeParseException} sessizce yutulur ve bozuk dosya
     * bayat kabul edilerek {@code hasFresh} false döner.
     *
     * @throws Exception önbellek dosyası yazılamazsa
     */
    @Test
    void corruptFileConsideredStale() throws Exception {
        newStore();
        Files.writeString(dir.resolve("cache").resolve("price_THYAO.csv"), "bozuk-veri");

        assertFalse(newStore().hasFresh("THYAO"));
    }

    /**
     * Yazma-okama turunun satırları birebir koruması.
     * <p>
     * Senaryo: THYAO sembolüne iki bar yazılır. Beklenen davranış: dosya satırları
     * {@code tarih,kapanış,hacim} biçiminde yazımla birebir aynı üretir; okuma da
     * aynı iki barı orijinal değerleriyle geri verir (kayan nokta dönüşümü
     * uygulanmaz).
     *
     * @throws Exception önbellek dosyası okunamazsa
     */
    @Test
    void writeReadRoundTrip() throws Exception {
        CsvPriceCache store = newStore();
        store.write("THYAO", List.of(new Bar("2026-01-01", 240.0, 1000),
                new Bar("2026-01-02", 241.0, 900)));

        assertEquals(List.of("2026-01-01,240.0,1000", "2026-01-02,241.0,900"),
                Files.readAllLines(dir.resolve("cache").resolve("price_THYAO.csv")));
        assertEquals(List.of(new Bar("2026-01-01", 240, 1000),
                new Bar("2026-01-02", 241, 900)), store.read("THYAO"));
    }
}