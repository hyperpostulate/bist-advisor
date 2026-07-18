package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.config.ModelType;
import smile.classification.SVM;
import smile.math.kernel.GaussianKernel;

import java.util.ArrayList;
import java.util.List;

/**
 * Cok sinifli (AL/SAT/TUT) SVM. SMILE SVM ikili oldugu icin
 * one-vs-rest sarici kullanilir: her sinif icin ayri bir binary SVM.
 */
public class SvmStrategy implements ModelStrategy {
    private List<SVM<double[]>> binaries = new ArrayList<>();
    private int numClasses = 3;

    @Override
    public void train(double[][] features, int[] labels) {
        binaries.clear();
        for (int c = 0; c < numClasses; c++) {
            int[] binary = new int[labels.length];
            for (int i = 0; i < labels.length; i++) binary[i] = (labels[i] == c) ? 1 : -1;
            binaries.add(SVM.fit(features, binary, new GaussianKernel(1.0),
                    new SVM.Options(1.0, 1E-3, 100)));
        }
    }

    @Override
    public double[] predict(double[] features) {
        double bestScore = -1;
        int bestClass = 0;
        for (int c = 0; c < numClasses; c++) {
            // binary SVM: 1 sinifi bu sinif, 0 digerleri. Skoru 1 sinifi olasiligi olarak al.
            int pred = binaries.get(c).predict(features);
            double score = (pred == 1) ? 0.6 : 0.4;
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
