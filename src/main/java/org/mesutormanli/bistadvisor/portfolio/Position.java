package org.mesutormanli.bistadvisor.portfolio;

/**
 * Portföydeki tek bir pozisyonu temsil eden değiştirilemez kayıt (record).
 * <p>
 * Bileşenler: {@code symbol} hisse sembolü; {@code lots} tutulan lot sayısı;
 * {@code avgCost} lot başına ortalama maliyet (TL). Compact constructor sembolü
 * BÜYÜK HARFE çevirir; normalizasyon bu tek noktadan yapılır.
 *
 * @param symbol  hisse sembolü (compact constructor içinde büyütülür)
 * @param lots    tutulan lot sayısı
 * @param avgCost lot başına ortalama maliyet (TL)
 */
public record Position(String symbol, int lots, double avgCost) {
    /**
     * Sembolü büyük harfe çevirerek kaydı oluşturur.
     *
     * @param symbol  hisse sembolü
     * @param lots    tutulan lot sayısı
     * @param avgCost lot başına ortalama maliyet (TL)
     * @throws NullPointerException sembol {@code null} ise
     * @implNote Normalizasyon tek noktadan, bu compact constructor içinde yapılır.
     */
    public Position {
        symbol = symbol.toUpperCase();
    }
}
