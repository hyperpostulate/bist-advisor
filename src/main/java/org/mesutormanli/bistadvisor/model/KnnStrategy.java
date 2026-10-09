package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.config.AnalysisType;
import org.mesutormanli.bistadvisor.config.ModelType;
import smile.classification.KNN;

/**
 * k-En Yakın Komşu (k-NN) sınıflandırma stratejisi; SMILE {@link KNN} modelini kullanır.
 *
 * <p>Eğitimde etiketler {@link ClassSpace} ile sıkı indekslere sıkıştırılır; böylece SMILE'ın
 * "olasılık vektörü boyutu = eğitimdeki sınıf sayısı" beklentisi karşılanır ve eğitim setinde
 * hiç oluşmayan sınıf tahminde istisna üretmez. Komşu sayısı {@code DEFAULT_K = 5} olup
 * örnek sayısına göre {@code min(5, örnek sayısı)} olarak daraltılır. Tek sınıflı eğitimde
 * model kurmaz. Üye metodlar {@code synchronized} olduğundan sınıf thread-safe'tir.
 */
public final class KnnStrategy implements ModelStrategy {

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
     * <p>Eğitilmemişse (sınıf tablosu yoksa) {@code {TUT, 0.0}} döner; tek sınıflı eğitimde
     * o sınıfı güven {@code 1.0} ile döner. Aksi halde komşu oylamasının olasılık dağılımı
     * doldurulur, kazanan sınıf ve o sınıfın olasılık değeri iki elemanlı dizi olarak verilir.
     *
     * @param features analiz tipinin boyutunda öznitelik vektörü
     * @return 2 elemanlı dizi: {@code [sınıf etiketi, olasılık/skor]}
     */
    @Override
    public synchronized double[] predict(double[] features) {
        if (classes == null || model == null) {
            return classes == null ? new double[]{Labeler.HOLD, 0.0}
                    : new double[]{classes.label(0), 1.0};
        }
        double[] prob = new double[classes.size()];
        int cls = model.predict(features, prob);
        return new double[]{classes.label(cls), prob[cls]};
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
}