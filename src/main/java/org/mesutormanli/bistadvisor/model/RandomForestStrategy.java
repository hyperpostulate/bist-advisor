package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.config.ModelType;
import smile.classification.RandomForest;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;

import java.util.ArrayList;
import java.util.List;

/**
 * Random Forest sınıflandırma stratejisi (one-vs-rest).
 * <p>
 * Her sınıf (AL, SAT, TUT) için ayrı bir ikili Random Forest modeli eğitir.
 * Tahmin aşamasında üç modelden en yüksek olasılık skoruna sahip sınıf seçilir.
 * SMILE kütüphanesinin {@link RandomForest} sınıfını kullanır.
 */
public final class RandomForestStrategy implements ModelStrategy {
    private final List<RandomForest> forests = new ArrayList<>();
    private DataFrame schemaFrame;
    private static final int NUM_CLASSES = 3;

    /**
     * Her sınıf için bir ikili Random Forest modeli eğitir (one-vs-rest).
     *
     * @param features {@code double[N][11]} eğitim verisi
     * @param labels   {@code int[N]} etiketler (0=AL, 1=SAT, 2=TUT)
     */
    @Override
    public synchronized void train(double[][] features, int[] labels) {
        String[] names = FeatureFrame.names();
        DataFrame df = DataFrame.of(features, names);
        int[][] cls2d = new int[labels.length][1];
        for (int i = 0; i < labels.length; i++) cls2d[i][0] = labels[i];
        DataFrame clsDf = DataFrame.of(cls2d, "sinif");
        df = df.merge(clsDf);
        this.schemaFrame = df;

        forests.clear();
        for (int c = 0; c < NUM_CLASSES; c++) {
            int[] binary = new int[labels.length];
            for (int i = 0; i < labels.length; i++) binary[i] = (labels[i] == c) ? 1 : 0;

            DataFrame bdf = DataFrame.of(features, names);
            int[][] bcls = new int[labels.length][1];
            for (int i = 0; i < labels.length; i++) bcls[i][0] = binary[i];
            bdf = bdf.merge(DataFrame.of(bcls, "sinif"));
            forests.add(RandomForest.fit(Formula.lhs("sinif"), bdf));
        }
    }

    /**
     * Üç ikili Random Forest modelini çalıştırır ve en yüksek skorlu sınıfı döndürür.
     *
     * @param features 11 boyutlu öznitelik vektörü
     * @return {@code [sınıf, skor]} — sınıf: 0=AL, 1=SAT, 2=TUT
     */
    @Override
    public synchronized double[] predict(double[] features) {
        if (schemaFrame == null || forests.isEmpty()) {
            return new double[]{0, 0.0};
        }
        Tuple t = Tuple.of(schemaFrame.schema(), java.util.Arrays.stream(features).boxed().toArray());
        double bestScore = -1;
        int bestClass = 0;
        for (int c = 0; c < NUM_CLASSES; c++) {
            double[] prob = new double[2];
            forests.get(c).predict(t, prob);
            double score = prob[1];
            if (score > bestScore) {
                bestScore = score;
                bestClass = c;
            }
        }
        return new double[]{bestClass, bestScore};
    }

    @Override
    public ModelType type() { return ModelType.RANDOM_FOREST; }
}
