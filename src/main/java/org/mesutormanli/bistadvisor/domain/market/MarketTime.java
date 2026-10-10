package org.mesutormanli.bistadvisor.domain.market;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Piyasa zamanı yardımcıları; tüm tarih hesaplarında {@code Europe/Istanbul}
 * saat dilimini esas alır.
 * <p>
 * Fiyat serilerinin tarihlenmesi ve önbellek tazelik kontrolleri bu sınıfın
 * {@link #today()} ve {@link #isFreshEnough(LocalDate)} yardımcıları üzerinden
 * yürütülür; böylece "bugün" kavramı uygulama genelinde tek bir saat dilimine
 * sabitlenir.
 */
public final class MarketTime {

    /** Piyasa saat dilimi. */
    public static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    private static final int FRESH_WINDOW_DAYS = 4;

    private MarketTime() {}

    /**
     * Piyasa saat dilimine göre bugünün tarihini döndürür.
     *
     * @return {@code Europe/Istanbul} saat diliminde bugünün tarihi
     */
    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /**
     * Son bar tarihinin 4 günlük tazelik penceresi içinde olup olmadığını belirler.
     * <p>
     * Son bar tarihi {@code bugün − 4} gününden eski değilse veri "taze" kabul
     * edilir. Hafta sonu ve resmi tatillerde yeni bar oluşmadığından tolerans
     * 4 gündür.
     *
     * @param lastBarDate kontrol edilecek son bar tarihi
     * @return penceredeyse {@code true}; tarih {@code null} ise {@code false}
     */
    public static boolean isFreshEnough(LocalDate lastBarDate) {
        return lastBarDate != null && !lastBarDate.isBefore(today().minusDays(FRESH_WINDOW_DAYS));
    }
}