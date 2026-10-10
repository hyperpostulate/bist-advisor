package org.mesutormanli.bistadvisor.domain.ml;

import org.mesutormanli.bistadvisor.domain.settings.AnalysisType;
import org.mesutormanli.bistadvisor.domain.settings.ModelType;
import smile.classification.KNN;

/**
 * k-En Yakın Komşu (k-NN) sınıflandırma stratejisi; SMILE {@link KNN} modelini kullanır.
 *
 * <p>Eğitimde etiketler {@link ClassSpace} ile sıkı indekslere sıkıştırılır; böylece SMILE'ın
 * "olasılık vektörü boyutu = eğitimdeki sınıf sayısı" beklentisi karşılanır ve eğitim setinde
 * hiç oluşmayan sınıf tahminde istisna üretmez. Komşu sayısı {@code DEFAULT_K = 5} olup
 * örnek sayısına göre {@code min(5, örnek sayısı)} olarak daraltılır. Tek sınıflı eğitimde
 * model kurmaz. Güven skoru komşu oy oranıdır ve {@code 1/K = 0.2} adımlıdır; bu yüzden
 * {@link #scoreResolution()} bu çözünürlüğü bildirir. Üye metodlar {@code synchronized}
 * olduğundan sınıf thread-safe'tir.
 */
public final class KnnStrategy implements ModelStrategy {

    /** Varsayılan komşu sayısı. */
    public static final int DEFAULT_K = 5;

    private KNN<double[]> model;
    private ClassSpace classes;

    /**
     * Eğitir: k-NN modelini komşu tabanlı öğrenme için eğitim seti üzerinde kurar.
     *
     * <p>Önce sınıfları {@link ClassSpace} ile sıkıştırır; tek sınıf varsa SMILE'ın tek
     * sınıflı eğitim hatasını önlemek için model kurmaz. Komşu sayısı
     * {@code min(DEFAULT_K = 5, örnek sayısı)} olarak ayarlanır.
     *
     * @param features eğitim seti öznitelik matrisi ({@code double[N][k]}; {@code k} =
     *                 {@code FeatureVector.dimension(type)})
     * @param labels   eğitim seti etiketleri ({@code int[N]}; 0=AL, 1=SAT, 2=TUT)
     * @param type     eğitimde kullanılan analiz tipi; matrisin sütun sayısını belirler
     */
    @Override
    public synchronized void train(double[][] features, int[] labels, AnalysisType type) {
        this.classes = ClassSpace.of(labels);
        this.model = null;
        if (classes.size() == 1) return;
        this.model = KNN.fit(features, classes.compress(labels),
                Math.min(DEFAULT_K, features.length));
    }

    /**
     * Tahmin eder: en yakın komşuların oylamasından kazanan sınıfı ve olasılığını döndürür.
     *
     * <p>Eğitilmemişse (sınıf tablosu yoksa) {@link Prediction#NONE} döner; tek sınıflı
     * eğitimde o sınıfı güven {@code 1.0} ile döner. Aksi halde komşu oylamasının olasılık
     * dağılımı doldurulur, kazanan sınıf ve o sınıfın olasılık değeri döndürülür.
     *
     * @param features analiz tipinin boyutunda öznitelik vektörü
     * @return sınıf etiketi ve olasılık/skor içeren tahmin
     */
    @Override
    public synchronized Prediction predict(double[] features) {
        if (classes == null || model == null) {
            return classes == null ? Prediction.NONE
                    : new Prediction(classes.label(0), 1.0);
        }
        double[] prob = new double[classes.size()];
        int cls = model.predict(features, prob);
        return new Prediction(classes.label(cls), prob[cls]);
    }

    /**
     * Bildirir: stratejinin {@link ModelType#KNN} türünde olduğunu döndürür.
     *
     * @return model türü ({@link ModelType#KNN})
     */
    @Override
    public ModelType type() {
        return ModelType.KNN;
    }

    /**
     * Bildirir: güven skorunun komşu oy oranından geldiğini ({@code 1/K} adımlı) bildirir.
     *
     * @return skor çözünürlüğü ({@code 1.0 / DEFAULT_K = 0.2})
     */
    @Override
    public double scoreResolution() {
        return 1.0 / DEFAULT_K;
    }
}