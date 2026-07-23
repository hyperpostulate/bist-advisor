package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.config.ModelType;
import smile.classification.SVM;
import smile.math.kernel.GaussianKernel;

import java.util.ArrayList;
import java.util.List;

/**
 * One-vs-rest SVM stratejisi (Gaussian kernel). SMILE SVM ikili
 * siniflandirici oldugundan her sinif (AL/SAT/TUT) icin ayri bir
 * binary SVM egitilir, tahminde en yuksek sigmoid-skorlu sinif secilir.
 */
public class SvmStrategy implements ModelStrategy {
    private List<SVM<double[]>> binaries = new ArrayList<>();
    private int numClasses = 3;

    @Override
    public synchronized void train(double[][] features, int[] labels) {
        binaries.clear();
        for (int c = 0; c < numClasses; c++) {
            int[] binary = new int[labels.length];
            for (int i = 0; i < labels.length; i++) binary[i] = (labels[i] == c) ? 1 : -1;
            binaries.add(SVM.fit(features, binary, new GaussianKernel(1.0),
                    new SVM.Options(1.0, 1E-3, 100)));
        }
    }

    /** Her binary SVM'in decision value'sunu sigmoid ile 0..1'e cevirir, en yuksek skorlu sinifi secer. */
    @Override
    public synchronized double[] predict(double[] features) {
        double bestScore = -1;
        int bestClass = 0;
        for (int c = 0; c < numClasses; c++) {
            double decision = binaries.get(c).score(features);
            double score = 1.0 / (1.0 + Math.exp(-decision));
            if (score > bestScore) {
                bestScore = score;
                bestClass = c;
            }
        }
        return new double[]{bestClass, bestScore};
    }

    @Override
    public ModelType type() {
        return ModelType.SVM;
    }
}
