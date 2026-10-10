package org.mesutormanli.bistadvisor.adapters.yahoo;

import org.mesutormanli.bistadvisor.domain.market.Fundamentals;
import org.mesutormanli.bistadvisor.domain.market.MarketTime;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Temel göstergeleri gün bazlı bellek içi önbellekte tutan yardımcı.
 * <p>
 * Bir giriş yalnızca alındığı gün geçerlidir: {@link #getIfFresh(String)} aynı gün içindeki
 * tekrarlı çağrıları karşılar, ertesi gün veri eskimiş sayılır ve kaynak yeniden çağrılır.
 * Erişim metodları thread-safe'tir ({@link ConcurrentHashMap}).
 */
@Component
public class FundamentalsDayCache {

    /**
     * Gün bazlı önbellek girişi: {@code day} önbelleğin geçerli olduğu tarihi,
     * {@code fundamentals} o gün alınan temel göstergeleri taşır.
     *
     * @param day          önbelleğin geçerli olduğu tarih
     * @param fundamentals o gün alınan temel göstergeler
     */
    private record CachedFundamentals(LocalDate day, Fundamentals fundamentals) {}

    private final Map<String, CachedFundamentals> cache = new ConcurrentHashMap<>();

    /**
     * Sembolün bugün için önbelleklenmiş temel göstergesini döndürür.
     *
     * @param key önbellek anahtarı (ör. Yahoo sembolü)
     * @return aynı gün alınmış temel göstergeler; giriş yoksa ya da başka güne aitse {@code null}
     */
    public Fundamentals getIfFresh(String key) {
        CachedFundamentals hit = cache.get(key);
        return hit != null && hit.day().equals(MarketTime.today()) ? hit.fundamentals() : null;
    }

    /**
     * Sembolün temel göstergelerini bugünün girişi olarak önbelleğe alır.
     *
     * @param key           önbellek anahtarı (ör. Yahoo sembolü)
     * @param fundamentals  o gün alınan temel göstergeler
     */
    public void put(String key, Fundamentals fundamentals) {
        cache.put(key, new CachedFundamentals(MarketTime.today(), fundamentals));
    }
}