package org.mesutormanli.bistadvisor.ports;

import org.mesutormanli.bistadvisor.domain.market.Bar;

import java.util.List;

/**
 * Yerel fiyat serisi önbelleğinin dışa açık sözleşmesi (port).
 * <p>
 * Uygulama katmanı fiyat serilerini bu arayüz üzerinden okur/yazar; somut depo
 * (ör. CSV dosyaları) adaptör katmanında bu sözleşmeyi uygular. Tazelik kararı
 * önbelleğe aittir: {@link #hasFresh(String)} son barın 4 günlük tazelik penceresinde
 * olup olmadığını söyler. Erişim hataları istisna yaymaz (boş okuma döner, yazma
 * sessizce yutulur).
 */
public interface PriceCache {

    /**
     * Sembolün önbellekte taze fiyat verisi tutup tutmadığını belirler.
     *
     * @param symbol hisse sembolü
     * @return önbellekteki son bar {@code bugün − 4} gününden eski değilse {@code true}
     */
    boolean hasFresh(String symbol);

    /**
     * Sembolün önbellekteki fiyat serisini okur.
     *
     * @param symbol hisse sembolü
     * @return bar listesi; kayıt yoksa veya okuma hatasında boş liste
     */
    List<Bar> read(String symbol);

    /**
     * Sembolün fiyat serisini önbelleğe yazar; mevcut kaydın yerini alır.
     *
     * @param symbol hisse sembolü
     * @param bars   yazılacak bar listesi
     */
    void write(String symbol, List<Bar> bars);
}