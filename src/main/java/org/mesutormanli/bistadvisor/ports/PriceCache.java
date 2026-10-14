package org.mesutormanli.bistadvisor.ports;

import org.mesutormanli.bistadvisor.domain.market.Bar;
import org.mesutormanli.bistadvisor.domain.market.MarketTime;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
     * Zaten okunmuş bir serinin taze olup olmadığını belirler.
     * <p>
     * {@link #hasFresh(String)} dosyayı ayrıca okuduğu için, seriyi okuduktan
     * sonra tazeliğini bu metotla sınamak ek disk okumasını önler (aynı tazelik
     * kuralı uygulanır). Varsayılan uygulama son bar tarihini
     * {@link MarketTime#isFreshEnough(LocalDate)} ile sınar; geçersiz/eksik
     * tarihte ve boş seride {@code false} döner.
     *
     * @param bars önbellekten okunmuş bar serisi
     * @return seri taze ise {@code true}; boş veya tarihi çözümlenemiyorsa {@code false}
     */
    default boolean isFresh(List<Bar> bars) {
        if (bars == null || bars.isEmpty()) return false;
        try {
            return MarketTime.isFreshEnough(LocalDate.parse(bars.getLast().date()));
        } catch (DateTimeParseException e) {
            return false;
        }
    }

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