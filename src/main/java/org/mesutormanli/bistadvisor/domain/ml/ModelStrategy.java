package org.mesutormanli.bistadvisor.domain.ml;

import org.mesutormanli.bistadvisor.domain.settings.AnalysisType;
import org.mesutormanli.bistadvisor.domain.settings.ModelType;

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
 * ({@code TECHNICAL} → 6, {@code FUNDAMENTAL} → 5, {@code TECHNICAL_FUNDAMENTAL} → 11);
 * {@link #predict(double[])} tek bir öznitelik vektörü için sınıf ve güven skoru içeren
 * {@link Prediction} döndürür; {@link #type()} stratejinin eşleştiği {@link ModelType} sabitini
 * bildirir. Tahmin vektörünün genişliği, modelin eğitildiği analiz tipinin genişliğiyle aynı
 * olmalıdır. Somut stratejilerin üye metodları {@code synchronized} olduğu için örnekler
 * thread-safe'tir.
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
     * @return sınıf etiketi ve skoru içeren tahmin (skor {@code [0,1]} aralığında);
     *         eğitilmemiş modellerde {@link Prediction#NONE}
     */
    Prediction predict(double[] features);

    /**
     * Bildirir: stratejinin eşleştiği model türünü döndürür.
     *
     * @return eşleşen {@link ModelType} sabiti
     */
    ModelType type();

    /**
     * Bildirir: modelin güven skorlarının aldığı en küçük adımın (ızgara çözünürlüğünün)
     * boyutunu döndürür.
     *
     * <p>Oy tabanlı modellerde güven, komşu oy oranıdır ve kesirli değerler alabilir
     * (ör. KNN'de {@code 1/K} adımlı). Karar eşiklerinin bu ızgaraya göre yorumlanması
     * gerekir (bkz. {@code domain.advisor.ScoreGate}). Sürekli skorlu modeller 0 döndürür.</p>
     *
     * @return skor çözünürlüğü; sürekli skorlar için 0
     */
    default double scoreResolution() {
        return 0.0;
    }
}