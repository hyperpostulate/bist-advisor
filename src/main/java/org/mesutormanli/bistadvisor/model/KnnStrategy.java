package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.config.ModelType;
import smile.classification.KNN;

/**
 * k-En Yakın Komşu (k-NN) sınıflandırma stratejisi (k=5, varsayılan).
 * <p>
 * SMILE kütüphanesinin {@link KNN} sınıfını kullanır. Eğitim ve tahmin
 * işlemleri thread-safe olacak şekilde senkronize edilmiştir.
 */
public final class KnnStrategy implements ModelStrategy {
    private KNN<double[]> model;

    /**
     * k-NN modelini verilen öznitelik matrisi ve etiketlerle eğitir.
     *
     * @param features {@code double[N][11]} eğitim verisi
     * @param labels   {@code int[N]} etiketler (0=AL, 1=SAT, 2=TUT)
     */
    @Override
    public synchronized void train(double[][] features, int[] labels) {
        this.model = KNN.fit(features, labels);
    }

    /**
     * Bir öznitelik vektörü için sınıf tahmini ve olasılık skoru döndürür.
     *
     * @param features 11 boyutlu öznitelik vektörü
     * @return {@code [sınıf, skor]} — sınıf: 0=AL, 1=SAT, 2=TUT
     */
    @Override
    public synchronized double[] predict(double[] features) {
        double[] prob = new double[3];
        int cls = model.predict(features, prob);
        double score = (cls < prob.length) ? prob[cls] : 0.0;
        return new double[]{cls, score};
    }

    @Override
    public ModelType type() {
        return ModelType.KNN;
    }
}
