package org.mesutormanli.bistadvisor.features;

import org.junit.jupiter.api.Test;
import org.mesutormanli.bistadvisor.data.YahooClient.Fundamentals;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link FeatureVector#normalize()} sözleşmesinin testleri: tüm öznitelikler
 * {@code [0, 1]} aralığına ölçeklenmeli ve aşırı değerler kırpılmalı.
 * <p>
 * EĞİTİM/TAHMİN hat zincirleri {@code fv.normalize().toArray()} kalıbını kullanır;
 * dönüş değeri yok sayılırsa model ham ölçekte eğitilir (bkz. ANALYSIS.md BUG-02).
 */
class FeatureVectorTest {

    @Test
    void normalizeDegerleriOlcekler() {
        FeatureVector fv = new FeatureVector(50, 0, 0, 0, 0.02, 1, 25, 5, 2, 0.1, 0.2);
        double[] n = fv.normalize().toArray();

        assertEquals(0.5, n[0], 1e-9);          // rsi/100
        assertEquals(0.5, n[1], 1e-9);          // (sma20Ratio + 1) / 2
        assertEquals(0.5, n[2], 1e-9);
        assertEquals(0.5, n[3], 1e-9);          // macd * 10 + 0.5
        assertEquals(1.0, n[4], 1e-9);          // volatility * 50
        assertEquals(1.0 / 3, n[5], 1e-9);      // volumeRatio / 3
        assertEquals(0.5, n[6], 1e-9);          // fk / 50
        assertEquals(0.5, n[7], 1e-9);          // pdDd / 10
        assertEquals(0.2, n[8], 1e-9);          // dividendYield / 10
        assertEquals(0.6, n[9], 1e-9);          // (growth * 100 + 50) / 100
        assertEquals(0.7, n[10], 1e-9);         // (roe * 100 + 50) / 100
    }

    @Test
    void normalizeAsiriDegerleriKisar() {
        FeatureVector fv = new FeatureVector(200, 5, -5, 1, 1, 10, 200, 50, 30, 10, -1);
        double[] n = fv.normalize().toArray();

        for (double v : n) {
            assertTrue(v >= 0.0 && v <= 1.0, "[0,1] dışı değer: " + v);
        }
        assertEquals(1.0, n[0], 1e-9);   // rsi üst sınır
        assertEquals(0.0, n[2], 1e-9);   // sma50Ratio alt sınır
        assertEquals(0.0, n[10], 1e-9);  // roe alt sınır
    }

    @Test
    void fromBarsHamDegerleriUretirVeNormalizeAyriNesneDoner() {
        List<TechnicalFeatures.Bar> bars = List.of(
                new TechnicalFeatures.Bar("2026-01-01", 100, 1000),
                new TechnicalFeatures.Bar("2026-01-02", 101, 1200),
                new TechnicalFeatures.Bar("2026-01-03", 102, 900));
        FeatureVector raw = FeatureVector.fromBars(Fundamentals.EMPTY, bars);
        FeatureVector normalized = raw.normalize();

        assertNotSame(raw, normalized);
        // ham RSI 0..100 dışında taşabilir, normalize edilmiş her zaman [0,1]
        assertTrue(normalized.rsi() >= 0 && normalized.rsi() <= 1);
    }
}