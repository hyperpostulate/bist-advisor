package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.config.ModelType;
import smile.classification.KNN;

/**
 * KNN tabanli strateji (SMILE 6.2.3). Varsayilan komsu sayisi 5.
 */
public class KnnStrategy implements ModelStrategy {
    private KNN<double[]> model;

    @Override
    public synchronized void train(double[][] features, int[] labels) {
        this.model = KNN.fit(features, labels);
    }

    @Override
    public synchronized double[] predict(double[] features) {
        double[] prob = new double[3];
        int cls = model.predict(features, prob);
        double score = (cls < prob.length) ? prob[cls] : 0.0;
        return new double[]{cls, score};
    }

    @Override
    public ModelType type() {
        return ModelType.KNN;
    }
}
