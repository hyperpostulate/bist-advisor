package org.mesutormanli.bistadvisor.features;

import org.junit.jupiter.api.Test;
import org.mesutormanli.bistadvisor.data.YahooClient.Fundamentals;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link FeatureVector#normalize} sözleşmesinin testleri: ham göstergelerin
 * bilinen ölçeklerde {@code [0,1]} aralığına indirilmesi, uç değerlerin
 * kırpılması ve normalleştirmenin özgün nesneyi bozmadan yeni nesne döndürmesi.
 * <p>
 * Sıra ve ölçekler şöyledir: {@code rsi/100}, {@code (sma20Ratio+1)/2},
 * {@code (sma50Ratio+1)/2}, {@code macd*10+0.5}, {@code volatility*50},
 * {@code volumeRatio/3}, {@code fk/50}, {@code pdDd/10}, {@code dividendYield/10}
 * ile kâr büyümesi ve ROE için {@code (x*100+50)/100}.
 */
class FeatureVectorTest {

    /**
     * Tipik ham değerlerin bilinen ölçeklerde {@code [0,1]}'e eşlenmesi.
     * <p>
     * Senaryo: rsi 50, sma20/sma50 oranları 0, macd 0, oynaklık 0,02, hacim oranı 1,
     * F/K 25, PD/DD 5, temettü verimi 2, kâr büyümesi 0,1 ve ROE 0,2 verilir.
     * Beklenen davranış: sırasıyla 0,5 (50/100), 0,5 ve 0,5 ((x+1)/2), 0,5
     * (macd*10+0,5), 1,0 (0,02*50), 1/3 (1/3), 0,5 (25/50), 0,5 (5/10), 0,2 (2/10),
     * 0,6 ve 0,7 ((x*100+50)/100) değerleri üretilir.
     */
    @Test
    void normalizeDegerleriOlcekler() {
        FeatureVector fv = new FeatureVector(50, 0, 0, 0, 0.02, 1, 25, 5, 2, 0.1, 0.2);
        double[] n = fv.normalize().toArray();

        assertEquals(0.5, n[0], 1e-9);
        assertEquals(0.5, n[1], 1e-9);
        assertEquals(0.5, n[2], 1e-9);
        assertEquals(0.5, n[3], 1e-9);
        assertEquals(1.0, n[4], 1e-9);
        assertEquals(1.0 / 3, n[5], 1e-9);
        assertEquals(0.5, n[6], 1e-9);
        assertEquals(0.5, n[7], 1e-9);
        assertEquals(0.2, n[8], 1e-9);
        assertEquals(0.6, n[9], 1e-9);
        assertEquals(0.7, n[10], 1e-9);
    }

    /**
     * Uç ham değerlerin {@code [0,1]}'e kırpılması.
     * <p>
     * Senaryo: sınırları aşan değerler verilir — ham RSI 200, macd 5, sma50Ratio
     * −5, F/K 200, ROE −1 vb. Beklenen davranış: bütün bileşenler {@code [0,1]}
     * aralığında kalır; RSI 200 → 1,0'a, sma50Ratio −5 → 0'a ve ROE −1 → 0'a
     * kırpılır.
     */
    @Test
    void normalizeAsiriDegerleriKisar() {
        FeatureVector fv = new FeatureVector(200, 5, -5, 1, 1, 10, 200, 50, 30, 10, -1);
        double[] n = fv.normalize().toArray();

        for (double v : n) {
            assertTrue(v >= 0.0 && v <= 1.0, "[0,1] dışı değer: " + v);
        }
        assertEquals(1.0, n[0], 1e-9);
        assertEquals(0.0, n[2], 1e-9);
        assertEquals(0.0, n[10], 1e-9);
    }

    /**
     * {@code fromBars} değerlerinin ham kalması ve {@code normalize} sonucunun
     * ayrı bir nesne olması.
     * <p>
     * Senaryo: 3 barlık yükselen seriden {@link Fundamentals#EMPTY} ile bir
     * {@link FeatureVector} türetilir. Beklenen davranış: {@code fromBars} sonucu
     * ham (normalize edilmemiş) ölçekte kalır; {@code normalize()} özgün nesneyi
     * değiştirmeksizin AYRI bir nesne döndürür ({@code assertNotSame}) ve dönen
     * nesnenin RSI değeri {@code [0,1]} aralığındadır.
     */
    @Test
    void fromBarsHamDegerleriUretirVeNormalizeAyriNesneDoner() {
        List<TechnicalFeatures.Bar> bars = List.of(
                new TechnicalFeatures.Bar("2026-01-01", 100, 1000),
                new TechnicalFeatures.Bar("2026-01-02", 101, 1200),
                new TechnicalFeatures.Bar("2026-01-03", 102, 900));
        FeatureVector raw = FeatureVector.fromBars(Fundamentals.EMPTY, bars);
        FeatureVector normalized = raw.normalize();

        assertNotSame(raw, normalized);
        assertTrue(normalized.rsi() >= 0 && normalized.rsi() <= 1);
    }
}