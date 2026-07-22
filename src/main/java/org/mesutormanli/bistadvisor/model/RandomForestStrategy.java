package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.config.ModelType;
import smile.classification.RandomForest;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;

import java.util.ArrayList;
import java.util.List;

/**
 * RandomForest tabanli strateji (SMILE 6.2.3). SMILE RF cok sinifli veride
 * sensitivity metrigi binary gerektirdigi icin one-vs-rest sarici kullanilir:
 * her sinif (AL/SAT/TUT) icin ayri binary RF egitilir.
 */
public class RandomForestStrategy implements ModelStrategy {
    private final List<RandomForest> forests = new ArrayList<>();
    private DataFrame schemaFrame;
    private static final int NUM_CLASSES = 3;

    @Override
    public synchronized void train(double[][] features, int[] labels) {
        String[] names = FeatureFrame.names();
        DataFrame df = DataFrame.of(features, names);
        int[][] cls2d = new int[labels.length][1];
        for (int i = 0; i < labels.length; i++) cls2d[i][0] = labels[i];
        DataFrame clsDf = DataFrame.of(cls2d, "sinif");
        df = df.merge(clsDf);
        this.schemaFrame = df;
        Formula formula = Formula.lhs("sinif");

        forests.clear();
        for (int c = 0; c < NUM_CLASSES; c++) {
            double[][] binFeatures = features;
            int[] binary = new int[labels.length];
            for (int i = 0; i < labels.length; i++) binary[i] = (labels[i] == c) ? 1 : 0;
            // binary egitim icin ayri DataFrame kur
            DataFrame bdf = DataFrame.of(features, names);
            int[][] bcls = new int[labels.length][1];
            for (int i = 0; i < labels.length; i++) bcls[i][0] = binary[i];
            bdf = bdf.merge(DataFrame.of(bcls, "sinif"));
            forests.add(RandomForest.fit(Formula.lhs("sinif"), bdf));
        }
    }

    @Override
    public synchronized double[] predict(double[] features) {
        Tuple t = Tuple.of(schemaFrame.schema(), features);
        double bestScore = -1;
        int bestClass = 0;
        for (int c = 0; c < NUM_CLASSES; c++) {
            double[] prob = new double[2];
            forests.get(c).predict(t, prob);
            double score = prob.length >= 2 ? prob[1] : 0.5;
            if (score > bestScore) {
                bestScore = score;
                bestClass = c;
            }
        }
        return new double[]{bestClass, bestScore};
    }

    @Override
    public ModelType type() {
        return ModelType.RANDOM_FOREST;
    }
}
