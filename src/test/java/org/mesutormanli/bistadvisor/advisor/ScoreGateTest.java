package org.mesutormanli.bistadvisor.advisor;

import org.junit.jupiter.api.Test;
import org.mesutormanli.bistadvisor.config.ModelType;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ScoreGate} eşik yorumlamasının testleri:
 * KNN skorları k=5 için 1/5 paylarla konuşur; ham eşikler bu ızgaraya tavanlı
 * yuvarlanmalı ve en az 2/5 komşu anlaşması aranmalıdır. RandomForest/SVM eşikleri
 * kalibrasyon (P1) gelene kadar olduğu gibi korunur.
 */
class ScoreGateTest {

    private static final double DELTA = 1e-9;

    @Test
    void knnEsikleriOyIzgarasinaYuvarlanir() {
        assertEquals(0.4, ScoreGate.effectiveThreshold(ModelType.KNN, 0.20), DELTA); // 1/5 → 2/5
        assertEquals(0.4, ScoreGate.effectiveThreshold(ModelType.KNN, 0.25), DELTA);
        assertEquals(0.4, ScoreGate.effectiveThreshold(ModelType.KNN, 0.30), DELTA);
        assertEquals(0.6, ScoreGate.effectiveThreshold(ModelType.KNN, 0.60), DELTA);
        assertEquals(0.8, ScoreGate.effectiveThreshold(ModelType.KNN, 0.75), DELTA);
    }

    @Test
    void knnTekKomsuOyuKararUretmez() {
        // 1/5 pay (0.2) artık hiçbir eşikle geçmemeli
        assertFalse(ScoreGate.passes(ModelType.KNN, 0.20, 0.20));
        assertTrue(ScoreGate.passes(ModelType.KNN, 0.40, 0.20));
    }

    @Test
    void digerModellerdeTemelEsikKorunur() {
        assertEquals(0.60, ScoreGate.effectiveThreshold(ModelType.RANDOM_FOREST, 0.60), DELTA);
        assertEquals(0.30, ScoreGate.effectiveThreshold(ModelType.SVM, 0.30), DELTA);
        assertTrue(ScoreGate.passes(ModelType.RANDOM_FOREST, 0.60, 0.60));
        assertFalse(ScoreGate.passes(ModelType.SVM, 0.29, 0.30));
    }
}