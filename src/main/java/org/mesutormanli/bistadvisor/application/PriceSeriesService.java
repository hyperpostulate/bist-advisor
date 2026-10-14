package org.mesutormanli.bistadvisor.application;

import org.mesutormanli.bistadvisor.domain.market.Bar;
import org.mesutormanli.bistadvisor.ports.MarketDataSource;
import org.mesutormanli.bistadvisor.ports.PriceCache;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Fiyat serisi yükleme akışının tek sahibi: yerel CSV önbelleği ile piyasa
 * verisi kaynağı arasındaki orkestrasyonu yürütür.
 * <p>
 * Eski {@code DailyAdvisor} ve {@code ModelTrainer} içindeki iki kopya
 * {@code loadSeries} akışının yerini alır. Akış: önbellek taze değilse seri
 * kaynaktan çekilir ve önbelleğe yazılır; seri HER durumda önbellekten okunarak
 * döndürülür (yazılan ile okunan aynı codec'ten geçtiği için tutarlıdır).
 */
@Service
public class PriceSeriesService {

    private final PriceCache cache;
    private final MarketDataSource source;

    /**
     * Kurar: önbellek portunu ve piyasa verisi kaynağını enjekte eder.
     *
     * @param cache  yerel fiyat serisi önbelleği
     * @param source piyasa verisi kaynağı
     */
    public PriceSeriesService(PriceCache cache, MarketDataSource source) {
        this.cache = cache;
        this.source = source;
    }

    /**
     * Sembolün fiyat serisini yerel önbellekten okur, taze değilse kaynaktan
     * çekip önbelleği tazeler.
     * <p>
     * Akış tek disk okumasıyla yürür: seri bir kez okunur, tazeliği
     * {@link PriceCache#isFresh(List)} ile sınanır; taze ise doğrudan döner.
     * Bayat ise kaynaktan çekilen seri önbelleğe yazılır ve (aynı codec'ten
     * geçmiş) taze veri olarak döner. Kaynak boş dönerse eldeki (bayat) seri
     * korunur.
     *
     * @param symbol yüklenecek hissenin sembolü
     * @return sembolün bar serisi
     */
    public List<Bar> load(String symbol) {
        List<Bar> cached = cache.read(symbol);
        if (cache.isFresh(cached)) {
            return cached;
        }
        List<Bar> fetched = source.fetchPrices(symbol);
        if (!fetched.isEmpty()) {
            cache.write(symbol, fetched);
            return fetched;
        }
        return cached;
    }
}