package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.config.ModelType;

/**
 * Sınıflandırma modelleri için ortak sözleşmeyi tanımlayan mühürlü ({@code sealed}) arayüz.
 *
 * <p>Yalnızca üç somut stratejiye izin verir: {@link RandomForestStrategy} (rastgele orman),
 * {@link KnnStrategy} (k-en yakın komşu) ve {@link SvmStrategy} (destek vektör makinesi).
 *
 * <p>Sözleşme: {@link #train(double[], int[])} bir eğitim matrisi ({@code double[n][11]},
 * satırlar örnek, sütunlar öznitelik) ve etiket dizisi ({@code int[n]},
 * {@link Labeler#BUY}/{@link Labeler#SELL}/{@link Labeler#HOLD}) alır;
 * {@link #predict(double[])} tek bir öznitelik vektörü için 2 elemanlı bir dizi döndürür —
 * {@code [0]} tahmin edilen sınıf etiketi, {@code [1]} güven/skor değeri {@code [0,1]}
 * aralığında; {@link #type()} stratejinin eşleştiği {@link ModelType} sabitini bildirir.
 * Somut stratejilerin üye metodları {@code synchronized} olduğu için örnekler thread-safe'tir.
 */
public sealed interface ModelStrategy permits RandomForestStrategy, KnnStrategy, SvmStrategy {
    /**
     * Eğitir: modeli öznitelik matrisi ve sınıf etiketleri üzerinde yeniden kurar.
     *
     * @param features eğitim seti öznitelik matrisi ({@code double[n][11]})
     * @param labels   eğitim seti etiketleri ({@code int[n]})
     */
    void train(double[][] features, int[] labels);

    /**
     * Tahmin eder: tek bir öznitelik vektörü için sınıf ve güven skoru döndürür.
     *
     * @param features 11 boyutlu öznitelik vektörü
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
