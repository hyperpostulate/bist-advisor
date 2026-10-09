package org.mesutormanli.bistadvisor.advisor;

import org.junit.jupiter.api.Test;
import org.mesutormanli.bistadvisor.config.ModelType;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ScoreGate} eşik yuvarlama ve karar kapısı testleri.
 * <p>
 * KNN'de komşu oyları beş komşu ({@code KnnStrategy.DEFAULT_K = 5}) üzerinden
 * sayıldığından skorlar 0,2'lik ızgaradadır; eşikler bu ızgaraya yukarı
 * yuvarlanır ve en az iki komşu oyu (0,40) kadar olur. SVM ve RandomForest'te ise
 * taban eşik aynen korunur. Sayısal karşılaştırmalar sabit
 * {@code DELTA = 1e-9} kayan nokta toleransıyla yapılır.
 */
class ScoreGateTest {

    private static final double DELTA = 1e-9;

    /**
     * KNN eşiklerinin 0,2'lik oy ızgarasına yukarı yuvarlanması.
     * <p>
     * Senaryo: KNN için taban eşiğin 0,20, 0,25, 0,30, 0,60 ve 0,75 olduğu
     * durumlar denenir. Beklenen davranış: 0,20, 0,25 ve 0,30 tabanları en yakın
     * geçerli adım olan 0,40'a (iki komşu oyu tabanı) yuvarlanır; 0,60 aynen 0,60
     * kalır, 0,75 ise 0,80'e yuvarlanır.
     */
    @Test
    void knnEsikleriOyIzgarasinaYuvarlanir() {
        assertEquals(0.4, ScoreGate.effectiveThreshold(ModelType.KNN, 0.20), DELTA);
        assertEquals(0.4, ScoreGate.effectiveThreshold(ModelType.KNN, 0.25), DELTA);
        assertEquals(0.4, ScoreGate.effectiveThreshold(ModelType.KNN, 0.30), DELTA);
        assertEquals(0.6, ScoreGate.effectiveThreshold(ModelType.KNN, 0.60), DELTA);
        assertEquals(0.8, ScoreGate.effectiveThreshold(ModelType.KNN, 0.75), DELTA);
    }

    /**
     * Tek komşu oyunun tek başına karar üretememesi.
     * <p>
     * Senaryo: 0,20'lik tabana karşı 0,20 (tek komşu oyu) ve 0,40 (iki komşu oyu)
     * skorları denenir. Beklenen davranış: KNN'de etkin eşik en az 0,40 olduğundan
     * 0,20 skoru geçemez ({@code false}), 0,40 skoru ise geçer ({@code true}).
     */
    @Test
    void knnTekKomsuOyuKararUretmez() {
        assertFalse(ScoreGate.passes(ModelType.KNN, 0.20, 0.20));
        assertTrue(ScoreGate.passes(ModelType.KNN, 0.40, 0.20));
    }

    /**
     * SVM ve RandomForest'te taban eşiğin aynen korunması.
     * <p>
     * Senaryo: RandomForest'te 0,60 ve SVM'de 0,30 taban denenir. Beklenen
     * davranış: yuvarlama yapılmaz (0,60 → 0,60, 0,30 → 0,30); eşikle eşit 0,60
     * skoru RandomForest'te geçerken 0,29 skoru SVM'de 0,30 tabanını geçemez.
     */
    @Test
    void digerModellerdeTemelEsikKorunur() {
        assertEquals(0.60, ScoreGate.effectiveThreshold(ModelType.RANDOM_FOREST, 0.60), DELTA);
        assertEquals(0.30, ScoreGate.effectiveThreshold(ModelType.SVM, 0.30), DELTA);
        assertTrue(ScoreGate.passes(ModelType.RANDOM_FOREST, 0.60, 0.60));
        assertFalse(ScoreGate.passes(ModelType.SVM, 0.29, 0.30));
    }
}