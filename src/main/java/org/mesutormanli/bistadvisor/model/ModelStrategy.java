package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.config.ModelType;

/** ML model stratejisi arayuzu. RandomForest, SVM, KNN bu arayuzu uygular. */
public interface ModelStrategy {
    /** Verilen ozellik matrisi ve etiketlerle modeli egitir. */
    void train(double[][] features, int[] labels);

    /**
     * Tek bir ornek icin sinif ve guven skoru tahmin eder.
     * @return {sinif, skor} — sinif: 0=AL, 1=SAT, 2=TUT; skor 0..1
     */
    double[] predict(double[] features);

    /** Bu stratejinin karsilik geldigi ModelType enum sabiti. */
    ModelType type();
}
