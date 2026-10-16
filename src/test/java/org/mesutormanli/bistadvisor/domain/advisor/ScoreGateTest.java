package org.mesutormanli.bistadvisor.domain.advisor;

import org.junit.jupiter.api.Test;
import org.mesutormanli.bistadvisor.domain.ml.ModelStrategyFactory;
import org.mesutormanli.bistadvisor.domain.settings.ModelType;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ScoreGate} eşik yuvarlama ve karar kapısı testleri.
 * <p>
 * KNN'de komşu oyları beş komşu ({@code KnnStrategy.DEFAULT_K = 5}) üzerinden
 * sayıldığından skorlar 0,2'lik ızgaradadır; eşikler bu ızgaraya yukarı
 * yuvarlanır ve en az iki komşu oyu (0,40) kadar olur. SVM ve RandomForest'te ise
 * taban eşik aynen korunur. Kapı, model stratejisinin bildirdiği skor çözünürlüğüyle
 * ({@code ModelStrategyFactory.create(type).scoreResolution()}) kurulur. Sayısal
 * karşılaştırmalar sabit {@code DELTA = 1e-9} kayan nokta toleransıyla yapılır.
 */
class ScoreGateTest {

    private static final double DELTA = 1e-9;

    /**
     * Model türü için o modelin skor çözünürlüğüyle karar kapısı kurar.
     *
     * @param type model türü
     * @return modelin skor ızgarasına göre eşikleri yuvarlayan {@link ScoreGate}
     */
    private static ScoreGate gate(ModelType type) {
        return ScoreGate.discretized(ModelStrategyFactory.create(type).scoreResolution());
    }

    /**
     * KNN eşiklerinin 0,2'lik oy ızgarasına yukarı yuvarlanması.
     * <p>
     * Senaryo: KNN için taban eşiğin 0,20, 0,25, 0,30, 0,60 ve 0,75 olduğu
     * durumlar denenir. Beklenen davranış: 0,20, 0,25 ve 0,30 tabanları en yakın
     * geçerli adım olan 0,40'a (iki komşu oyu tabanı) yuvarlanır; 0,60 aynen 0,60
     * kalır, 0,75 ise 0,80'e yuvarlanır.
     */
    @Test
    void knnThresholdsRoundedToVoteGrid() {
        ScoreGate knn = gate(ModelType.KNN);
        assertEquals(0.4, knn.effectiveThreshold(0.20), DELTA);
        assertEquals(0.4, knn.effectiveThreshold(0.25), DELTA);
        assertEquals(0.4, knn.effectiveThreshold(0.30), DELTA);
        assertEquals(0.6, knn.effectiveThreshold(0.60), DELTA);
        assertEquals(0.8, knn.effectiveThreshold(0.75), DELTA);
    }

    /**
     * Tek komşu oyunun tek başına karar üretememesi.
     * <p>
     * Senaryo: 0,20'lik tabana karşı 0,20 (tek komşu oyu) ve 0,40 (iki komşu oyu)
     * skorları denenir. Beklenen davranış: KNN'de etkin eşik en az 0,40 olduğundan
     * 0,20 skoru geçemez ({@code false}), 0,40 skoru ise geçer ({@code true}).
     */
    @Test
    void singleKnnVoteProducesNoDecision() {
        ScoreGate knn = gate(ModelType.KNN);
        assertFalse(knn.passes(0.20, 0.20));
        assertTrue(knn.passes(0.40, 0.20));
    }

    /**
     * SVM ve RandomForest'te taban eşiğin aynen korunması.
     * <p>
     * Senaryo: RandomForest'te 0,60 ve SVM'de 0,30 taban denenir. Beklenen
     * davranış: yuvarlama yapılmaz (0,60 → 0,60, 0,30 → 0,30); eşikle eşit 0,60
     * skoru RandomForest'te geçerken 0,29 skoru SVM'de 0,30 tabanını geçemez.
     */
    @Test
    void baseThresholdPreservedForOtherModels() {
        assertEquals(0.60, gate(ModelType.RANDOM_FOREST).effectiveThreshold(0.60), DELTA);
        assertEquals(0.30, gate(ModelType.SVM).effectiveThreshold(0.30), DELTA);
        assertTrue(gate(ModelType.RANDOM_FOREST).passes(0.60, 0.60));
        assertFalse(gate(ModelType.SVM).passes(0.29, 0.30));
    }
}