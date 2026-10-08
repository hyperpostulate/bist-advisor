package org.mesutormanli.bistadvisor.data;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Piyasa verisi için ortak zaman yardımcıları.
 * <p>
 * Hem fiyat serilerinin tarihlenmesi (Yahoo JSON timestamp dönüşümü) hem de önbellek
 * tazelik kontrolleri bu sınıf üzerinden yapılır; böylece "bugün" kavramı tüm
 * uygulamada tek bir saat dilimine sabittir.
 */
public final class MarketTime {

    /** BIST işlem saatleri için saat dilimi. */
    public static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    /**
     * Önbellek tazelik penceresi (takvim günü). Hafta sonu, resmi tatil ve uzun bayram
     * molalarında "bugün" barı oluşmayacağı için toleranslı bir pencere kullanılır.
     */
    private static final int FRESH_WINDOW_DAYS = 4;

    private MarketTime() {}

    /**
     * Piyasa saat dilimine göre bugünün tarihini döndürür.
     *
     * @return bugün ({@code Europe/Istanbul})
     */
    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /**
     * Son bar tarihinin taze sayılıp sayılmayacağını belirler: taze, eğer son bar
     * {@code bugün - FRESH_WINDOW_DAYS} tarihinden önce değilse.
     *
     * @param lastBarDate önbellekteki son barın tarihi
     * @return {@code true} eğer veri tazeyse
     */
    public static boolean isFreshEnough(LocalDate lastBarDate) {
        return lastBarDate != null && !lastBarDate.isBefore(today().minusDays(FRESH_WINDOW_DAYS));
    }
}