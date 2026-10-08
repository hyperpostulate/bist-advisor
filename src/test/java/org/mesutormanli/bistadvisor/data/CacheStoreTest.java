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
 * {@link CacheStore} fiyat önbelleği ve tazelik kuralının testleri:
 * hafta sonu/resmi tatilde "bugün" barı oluşmayacağı için tazelik, son barın
 * {@link MarketTime} penceresi içinde olup olmadığına göre belirlenir. Ağa çıkmaz.
 */
class CacheStoreTest {

    @TempDir
    Path dir;

    private CacheStore newStore() {
        AppConfig cfg = new AppConfig();
        ReflectionTestUtils.setField(cfg, "cacheDir", dir.resolve("cache").toString());
        ReflectionTestUtils.setField(cfg, "stateFile", dir.resolve("state.yaml").toString());
        return new CacheStore(cfg);
    }

    @Test
    void dosyasiOlmayanSembolBayatSayilir() {
        assertFalse(newStore().hasFresh("YOK"));
    }

    @Test
    void sonBarToleransPenceresindeyseTazedir() {
        CacheStore store = newStore();
        LocalDate recent = MarketTime.today().minusDays(2);
        store.writeLines("THYAO", List.of(recent + ",240.0,1000"));

        assertTrue(store.hasFresh("THYAO"));
    }

    @Test
    void eskiBarBayatSayilir() {
        CacheStore store = newStore();
        store.writeLines("THYAO", List.of(MarketTime.today().minusDays(10) + ",240.0,1000"));

        assertFalse(store.hasFresh("THYAO"));
    }

    @Test
    void bozukDosyaBayatSayilir() {
        CacheStore store = newStore();
        store.writeLines("THYAO", List.of("bozuk-veri"));

        assertFalse(store.hasFresh("THYAO"));
    }

    @Test
    void yazmaOkumaYuvarlanir() {
        CacheStore store = newStore();
        store.writeLines("THYAO", List.of("2026-01-01,240.0,1000", "2026-01-02,241.0,900"));

        assertEquals(List.of("2026-01-01,240.0,1000", "2026-01-02,241.0,900"),
                store.readLines("THYAO"));
    }
}