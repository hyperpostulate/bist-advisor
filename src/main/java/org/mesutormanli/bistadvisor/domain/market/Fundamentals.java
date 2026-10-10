package org.mesutormanli.bistadvisor.domain.market;

/**
 * Temel gösterge kümesi: F/K, PD/DD, temettü verimi, kâr büyümesi ve özkaynak
 * kârlılığı (ROE).
 * <p>
 * Veri alınamadığını temsil eden {@link #EMPTY} örneği tüm bileşenlerinde 0 taşır.
 *
 * @param fk            F/K oranı
 * @param pdDd          PD/DD oranı
 * @param dividendYield temettü verimi
 * @param profitGrowth  kâr büyümesi
 * @param roe           özkaynak kârlılığı (ROE)
 */
public record Fundamentals(double fk, double pdDd, double dividendYield,
                           double profitGrowth, double roe) {

    /** Veri alınamadığını temsil eden (tümü 0) öbek. */
    public static final Fundamentals EMPTY = new Fundamentals(0, 0, 0, 0, 0);
}