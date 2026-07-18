package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.config.ModelType;

/**
 * ML model stratejisi. Egitim ve tahmin soyutlanir; uc model bu arayuzu uygular.
 */
public interface ModelStrategy {
    void train(double[][] features, int[] labels);

    /**
     * @return {sinif, guvenSkoru} — sinif: 0=AL, 1=SAT, 2=TUT; skor 0..1
     */
    double[] predict(double[] features);

    ModelType type();
}
