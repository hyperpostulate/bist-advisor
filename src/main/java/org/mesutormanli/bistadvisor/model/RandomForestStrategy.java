package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.config.ModelType;
import smile.classification.RandomForest;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.type.StructType;

import java.util.ArrayList;
import java.util.List;

/**
 * Random Forest sınıflandırma stratejisi (one-vs-rest).
 * <p>
 * Eğitim etiketlerinde <strong>gözlenen</strong> her sınıf için ayrı bir ikili Random Forest
 * modeli eğitilir. Etiketler {@link ClassSpace} ile sıkı indekslere eşlenir; eğitim setinde hiç
 * oluşmayan sınıf için ikili model eğitilmez ve tahminde istisna fırlatılmaz. Tek sınıf varsa
 * model eğitilmez, sabit tahmin döner.
 * <p>
 * Tahmin aşamasında modellerden en yüksek olasılık skoruna sahip sınıf seçilir. SMILE
 * kütüphanesinin {@link RandomForest} sınıfını kullanır.
 */
public final class RandomForestStrategy implements ModelStrategy {
    private final List<RandomForest> forests = new ArrayList<>();

    /** Yalnızca öznitelik kolonlarını içeren şema (etiket kolonu hariç). */
    private StructType schema;

    private ClassSpace classes;

    /**
     * Gözlenen her sınıf için bir ikili Random Forest modeli eğitir (one-vs-rest).
     *
     * @param features {@code double[N][11]} eğitim verisi
     * @param labels   {@code int[N]} etiketler (0=AL, 1=SAT, 2=TUT)
     */
    @Override
    public synchronized void train(double[][] features, int[] labels) {
        forests.clear();
        classes = ClassSpace.of(labels);
        String[] names = FeatureFrame.names();
        this.schema = DataFrame.of(features, names).schema();
        if (classes.size() == 1) return;
        for (int i = 0; i < classes.size(); i++) {
            int target = classes.label(i);
            int[][] bcls = new int[labels.length][1];
            for (int j = 0; j < labels.length; j++) bcls[j][0] = (labels[j] == target) ? 1 : 0;
            DataFrame bdf = DataFrame.of(features, names).merge(DataFrame.of(bcls, "sinif"));
            forests.add(RandomForest.fit(Formula.lhs("sinif"), bdf));
        }
    }

    /**
     * İkili Random Forest modellerini çalıştırır ve en yüksek skorlu sınıfı döndürür.
     *
     * @param features 11 boyutlu öznitelik vektörü
     * @return {@code [sınıf, skor]} — sınıf: 0=AL, 1=SAT, 2=TUT;
     *         henüz eğitim yapılmamışsa {@code [TUT, 0.0]},
     *         tek sınıflı eğitimde {@code [gözlenen sınıf, 1.0]}
     */
    @Override
    public synchronized double[] predict(double[] features) {
        if (classes == null) {
            return new double[]{Labeler.HOLD, 0.0};
        }
        if (classes.size() == 1) {
            return new double[]{classes.label(0), 1.0};
        }
        Tuple t = Tuple.of(schema, features);
        double bestScore = -1;
        int best = 0;
        for (int i = 0; i < forests.size(); i++) {
            double[] prob = new double[2];
            forests.get(i).predict(t, prob);
            if (prob[1] > bestScore) {
                bestScore = prob[1];
                best = i;
            }
        }
        return new double[]{classes.label(best), bestScore};
    }

    @Override
    public ModelType type() { return ModelType.RANDOM_FOREST; }
}