package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.config.ModelType;
import smile.classification.KNN;

/**
 * k-En Yakın Komşu (k-NN) sınıflandırma stratejisi (varsayılan k=5).
 * <p>
 * Eğitim etiketleri {@link ClassSpace} ile {@code 0..K-1} indekslere sıkıştırılır; böylece
 * eğitim setinde bir sınıf hiç oluşmamışsa tahmin istisna fırlatmaz, yalnızca gözlenen
 * sınıflar arasında seçim yapar. Tek sınıf varsa model eğitilmez (SMILE "Only one class"
 * hatası), sabit tahmin döner. Komşu sayısı eğitim örneği sayısından büyükse k otomatik
 * küçülür. SMILE kütüphanesinin {@link KNN} sınıfını kullanır. Eğitim ve tahmin işlemleri
 * thread-safe olacak şekilde senkronize edilmiştir.
 */
public final class KnnStrategy implements ModelStrategy {

    /** Varsayılan komşu sayısı (5). Skor eşiklerinin yuvarlanmasında da kullanılır
     *  (bkz. {@code ScoreGate}). */
    public static final int DEFAULT_K = 5;

    private KNN<double[]> model;
    private ClassSpace classes;

    /**
     * k-NN modelini verilen öznitelik matrisi ve etiketlerle eğitir.
     *
     * @param features {@code double[N][11]} eğitim verisi
     * @param labels   {@code int[N]} etiketler (0=AL, 1=SAT, 2=TUT)
     */
    @Override
    public synchronized void train(double[][] features, int[] labels) {
        this.classes = ClassSpace.of(labels);
        this.model = null;
        if (classes.size() == 1) return;
        this.model = KNN.fit(features, classes.compress(labels),
                Math.min(DEFAULT_K, features.length));
    }

    /**
     * Bir öznitelik vektörü için sınıf tahmini ve olasılık skoru döndürür.
     *
     * @param features 11 boyutlu öznitelik vektörü
     * @return {@code [sınıf, skor]} — sınıf: 0=AL, 1=SAT, 2=TUT;
     *         henüz eğitim yapılmamışsa {@code [TUT, 0.0]},
     *         tek sınıflı eğitimde {@code [gözlenen sınıf, 1.0]}
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

    @Override
    public ModelType type() {
        return ModelType.KNN;
    }
}