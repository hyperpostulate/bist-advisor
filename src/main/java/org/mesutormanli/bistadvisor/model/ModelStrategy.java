package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.config.ModelType;

/**
 * ML model stratejileri için sealed interface.
 * <p>
 * Tüm model stratejileri bu arayüzü uygular:
 * <ul>
 *   <li>{@link RandomForestStrategy}</li>
 *   <li>{@link KnnStrategy}</li>
 *   <li>{@link SvmStrategy}</li>
 * </ul>
 * Her strateji eğitim, tahmin ve tür bildirimi sağlamalıdır.
 */
public sealed interface ModelStrategy permits RandomForestStrategy, KnnStrategy, SvmStrategy {
    /**
     * Modeli verilen öznitelik matrisi ve etiketlerle eğitir.
     *
     * @param features {@code double[N][M]} eğitim öznitelikleri
     * @param labels   {@code int[N]} hedef etiketler
     */
    void train(double[][] features, int[] labels);

    /**
     * Bir öznitelik vektörü için sınıf tahmini ve güven skoru döndürür.
     *
     * @param features öznitelik vektörü
     * @return {@code [sınıf, skor]} dizisi
     */
    double[] predict(double[] features);

    /**
     * Bu stratejinin hangi {@link ModelType} sabitine karşılık geldiğini döndürür.
     *
     * @return model türü
     */
    ModelType type();
}
