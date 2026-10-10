package org.mesutormanli.bistadvisor.application;

/**
 * Tek pozisyonun rapor satırı: maliyet, güncel değer ve kâr/zarar.
 *
 * @param symbol       hisse sembolü
 * @param lots         tutulan lot sayısı
 * @param avgCost      lot başına ortalama maliyet (TL)
 * @param costTotal    toplam maliyet (lot × ortalama maliyet, TL)
 * @param currentPrice güncel fiyat; {@code null} = fiyat verisi yok
 * @param currentTotal toplam güncel değer; fiyat yoksa {@code null}
 * @param pnlPct       kâr/zarar oranı; fiyat yoksa {@code null}
 * @param pnlTl        kâr/zarar tutarı (TL); fiyat yoksa {@code null}
 */
public record PositionView(String symbol, int lots, double avgCost, double costTotal,
                           Double currentPrice, Double currentTotal,
                           Double pnlPct, Double pnlTl) {}