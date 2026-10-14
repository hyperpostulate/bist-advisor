package org.mesutormanli.bistadvisor.application;

import org.junit.jupiter.api.Test;
import org.mesutormanli.bistadvisor.domain.market.Bar;
import org.mesutormanli.bistadvisor.domain.market.Fundamentals;
import org.mesutormanli.bistadvisor.ports.MarketDataSource;
import org.mesutormanli.bistadvisor.ports.PriceCache;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PriceSeriesService} fiyat yükleme akışının testleri: taze önbellekte
 * kaynağa hiç çıkılmaması, bayat veride kaynağa gidilip önbelleğin tazelenmesi
 * ve boş yanıtta önbelleğe yazılmaması.
 * <p>
 * Port implementasyonları test içinde elle yazılmış sahtelerle (fake) kurulur;
 * hiçbir test ağa çıkmaz.
 */
class PriceSeriesServiceTest {

    /** Elle yazılmış önbellek sahtesi; tazelik kararı, okunan ve yazılan veriyi kaydeder. */
    private static final class FakeCache implements PriceCache {
        boolean fresh;
        List<Bar> data = List.of();
        String writtenSymbol;
        List<Bar> written;
        int reads;

        @Override
        public boolean hasFresh(String symbol) {
            return fresh;
        }

        @Override
        public boolean isFresh(List<Bar> bars) {
            return fresh;
        }

        @Override
        public List<Bar> read(String symbol) {
            reads++;
            return data;
        }

        @Override
        public void write(String symbol, List<Bar> bars) {
            writtenSymbol = symbol;
            written = bars;
            data = bars;
        }
    }

    /** Elle yazılmış kaynak sahtesi; çağrı sayısını ve dönecek seriyi kaydeder. */
    private static final class FakeSource implements MarketDataSource {
        int calls;
        List<Bar> prices = List.of();

        @Override
        public List<Bar> fetchPrices(String symbol) {
            calls++;
            return prices;
        }

        @Override
        public Fundamentals fetchFundamentals(String symbol) {
            return Fundamentals.EMPTY;
        }
    }

    /**
     * Taze önbellekte kaynağa hiç çıkılmaması.
     * <p>
     * Senaryo: önbellekte taze THYAO serisi vardır. Beklenen davranış: {@code load}
     * kaynaktan veri çekmez ({@code calls} 0 kalır) ve önbellekteki seriyi döndürür.
     */
    @Test
    void tazeOnbellekKaynakCagirmaz() {
        FakeCache cache = new FakeCache();
        FakeSource source = new FakeSource();
        cache.fresh = true;
        List<Bar> cached = List.of(new Bar("2026-01-01", 240, 1000));
        cache.data = cached;

        List<Bar> bars = new PriceSeriesService(cache, source).load("THYAO");

        assertEquals(0, source.calls);
        assertEquals(1, cache.reads, "önbellek tek okunmalı (çift okuma yok)");
        assertEquals(cached, bars);
    }

    /**
     * Bayat veride kaynağa gidilip önbelleğin tazelenmesi.
     * <p>
     * Senaryo: önbellek bayattır ve kaynak 2 bar döndürür. Beklenen davranış:
     * kaynak 1 kez çağrılır, seri THYAO anahtarıyla önbelleğe yazılır ve aynı
     * seri okunarak döndürülür.
     */
    @Test
    void bayatOnbellekKaynakTazeler() {
        FakeCache cache = new FakeCache();
        FakeSource source = new FakeSource();
        List<Bar> fetched = List.of(new Bar("2026-01-01", 240, 1000),
                new Bar("2026-01-02", 241, 900));
        source.prices = fetched;

        List<Bar> bars = new PriceSeriesService(cache, source).load("THYAO");

        assertEquals(1, source.calls);
        assertEquals(1, cache.reads, "önbellek tek okunmalı (çift okuma yok)");
        assertEquals("THYAO", cache.writtenSymbol);
        assertEquals(fetched, cache.written);
        assertEquals(fetched, bars);
    }

    /**
     * Boş yanıtın önbelleğe yazılmaması.
     * <p>
     * Senaryo: önbellek bayattır ve kaynak boş liste döndürür. Beklenen davranış:
     * kaynak yine 1 kez çağrılır ama önbelleğe yazılmaz ({@code written} null
     * kalır); boş okuma sonucu döner.
     */
    @Test
    void bosYanitOnbellegeYazilmaz() {
        FakeCache cache = new FakeCache();
        FakeSource source = new FakeSource();

        List<Bar> bars = new PriceSeriesService(cache, source).load("THYAO");

        assertEquals(1, source.calls);
        assertNull(cache.written);
        assertTrue(bars.isEmpty());
    }
}