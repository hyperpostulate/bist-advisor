package org.mesutormanli.bistadvisor.portfolio;

/**
 * Bir portföy pozisyonunu temsil eden kayıt.
 *
 * @param symbol  hisse senedi sembolü (büyük harfe çevrilir)
 * @param lots    lot sayısı
 * @param avgCost ortalama maliyet (TL/lot)
 */
public record Position(String symbol, int lots, double avgCost) {
    public Position {
        symbol = symbol.toUpperCase();
    }
}
