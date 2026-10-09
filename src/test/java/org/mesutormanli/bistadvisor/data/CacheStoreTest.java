package org.mesutormanli.bistadvisor.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mesutormanli.bistadvisor.config.AppConfig;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CacheStore} fiyat önbelleği sözlüğünün testleri: dosya varlığının
 * sembol başına tazelik kararıyla sonuçlanması, bozuk içeriğin bayat sayılması
 * ve yazma-okama turunun satırları birebir koruması.
 * <p>
 * Tazelik kuralı {@link MarketTime} ile birlikte okunmalıdır: son barın tarihi
 * bugünden en fazla 4 gün gerideyse ({@code FRESH_WINDOW_DAYS = 4}) seri tazedir.
 * Her test {@code @TempDir} altında kendi önbellek dizinini kullanır; diske ve
 * zamana bağlı tek unsur bugünün tarihidir.
 */
class CacheStoreTest {

    @TempDir
    Path dir;

    /**
     * Geçici dizin altında boş bir {@link CacheStore} kurar.
     * <p>
     * {@code @TempDir} ile sağlanan dizin altında {@code cache} ve
     * {@code state.yaml} yolları belirlenir; {@link AppConfig} alanları
     * {@code ReflectionTestUtils} ile doldurularak gerçek proje dizinlerine
     * dokunulmadan disk erişimi sağlanır.
     *
     * @return geçici dizine bağlı, içi boş yeni bir {@link CacheStore}
     */
    private CacheStore newStore() {
        AppConfig cfg = new AppConfig();
        ReflectionTestUtils.setField(cfg, "cacheDir", dir.resolve("cache").toString());
        ReflectionTestUtils.setField(cfg, "stateFile", dir.resolve("state.yaml").toString());
        return new CacheStore(cfg);
    }

    /**
     * Önbellek dosyası olmayan sembolün bayat sayılması.
     * <p>
     * Senaryo: daha önce hiç yazılmamış {@code YOK} sembolü sorgulanır. Beklenen
     * davranış: fiyat dosyası bulunamadığı için {@code hasFresh} false döner.
     */
    @Test
    void dosyasiOlmayanSembolBayatSayilir() {
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
    void sonBarToleransPenceresindeyseTazedir() {
        CacheStore store = newStore();
        LocalDate recent = MarketTime.today().minusDays(2);
        store.writeLines("THYAO", List.of(recent + ",240.0,1000"));

        assertTrue(store.hasFresh("THYAO"));
    }

    /**
     * Pencere dışındaki son barın bayat sayılması.
     * <p>
     * Senaryo: THYAO önbelleğindeki tek bar 10 günlüktür. Beklenen davranış: son
     * bar 4 günlük tolerans penceresini aştığı için {@code hasFresh} false döner.
     */
    @Test
    void eskiBarBayatSayilir() {
        CacheStore store = newStore();
        store.writeLines("THYAO", List.of(MarketTime.today().minusDays(10) + ",240.0,1000"));

        assertFalse(store.hasFresh("THYAO"));
    }

    /**
     * Tarihi çözümlenemeyen içeriğin bayat sayılması.
     * <p>
     * Senaryo: dosyaya tarih alanı içermeyen tek bozuk satır yazılır. Beklenen
     * davranış: {@code DateTimeParseException} sessizce yutulur ve bozuk dosya
     * bayat kabul edilerek {@code hasFresh} false döner.
     */
    @Test
    void bozukDosyaBayatSayilir() {
        CacheStore store = newStore();
        store.writeLines("THYAO", List.of("bozuk-veri"));

        assertFalse(store.hasFresh("THYAO"));
    }

    /**
     * Yazma-okama turunun satırları birebir koruması.
     * <p>
     * Senaryo: THYAO sembolüne iki CSV satırı yazılır. Beklenen davranış:
     * {@code readLines} aynı iki satırı yazımla birebir aynı içerik ve sırayla
     * döndürür; biçim veya kayan nokta dönüşümü uygulanmaz.
     */
    @Test
    void yazmaOkumaYuvarlanir() {
        CacheStore store = newStore();
        store.writeLines("THYAO", List.of("2026-01-01,240.0,1000", "2026-01-02,241.0,900"));

        assertEquals(List.of("2026-01-01,240.0,1000", "2026-01-02,241.0,900"),
                store.readLines("THYAO"));
    }
}