package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.config.AnalysisType;
import org.mesutormanli.bistadvisor.config.ModelType;

/**
 * Sınıflandırma modelleri için ortak sözleşmeyi tanımlayan mühürlü ({@code sealed}) arayüz.
 *
 * <p>Yalnızca üç somut stratejiye izin verir: {@link RandomForestStrategy} (rastgele orman),
 * {@link KnnStrategy} (k-en yakın komşu) ve {@link SvmStrategy} (destek vektör makinesi).
 *
 * <p>Sözleşme: {@link #train(double[], int[], AnalysisType)} bir eğitim matrisi
 * ({@code double[n][k]}, satırlar örnek, sütunlar öznitelik) ve etiket dizisi ({@code int[n]},
 * {@link Labeler#BUY}/{@link Labeler#SELL}/{@link Labeler#HOLD}) alır; burada {@code k}
 * matrisin sütun genişliği seçilen {@link AnalysisType} ile belirlenir
 * ({@code YALNIZCA_TEKNIK} → 6, {@code YALNIZCA_TEMEL} → 5, {@code TEKNIK_TEMEL} → 11);
 * {@link #predict(double[])} tek bir öznitelik vektörü için 2 elemanlı bir dizi döndürür —
 * {@code [0]} tahmin edilen sınıf etiketi, {@code [1]} güven/skor değeri {@code [0,1]}
 * aralığında; {@link #type()} stratejinin eşleştiği {@link ModelType} sabitini bildirir.
 * Tahmin vektörünün genişliği, modelin eğitildiği analiz tipinin genişliğiyle aynı olmalıdır.
 * Somut stratejilerin üye metodları {@code synchronized} olduğu için örnekler thread-safe'tir.
 */
public sealed interface ModelStrategy permits RandomForestStrategy, KnnStrategy, SvmStrategy {
    /**
     * Eğitir: modeli öznitelik matrisi ve sınıf etiketleri üzerinde yeniden kurar.
     *
     * @param features eğitim seti öznitelik matrisi ({@code double[n][k]}; {@code k} =
     *                 {@code FeatureVector.dimension(type)})
     * @param labels   eğitim seti etiketleri ({@code int[n]})
     * @param type     eğitimde kullanılan analiz tipi; matrisin sütun düzenini ve genişliğini
     *                 belirler (bkz. {@code FeatureVector.featureNames(type)})
     */
    void train(double[][] features, int[] labels, AnalysisType type);

    /**
     * Tahmin eder: tek bir öznitelik vektörü için sınıf ve güven skoru döndürür.
     *
     * @param features analiz tipinin boyutunda öznitelik vektörü
     *                 ({@code FeatureVector.dimension(type)} elemanlı)
     * @return 2 elemanlı dizi: {@code [sınıf etiketi, skor]} — skor {@code [0,1]} aralığında
     */
    double[] predict(double[] features);

    /**
     * Bildirir: stratejinin eşleştiği model türünü döndürür.
     *
     * @return eşleşen {@link ModelType} sabiti
     */
    ModelType type();
}
