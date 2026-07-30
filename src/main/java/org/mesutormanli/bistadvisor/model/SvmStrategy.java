package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.config.ModelType;
import smile.classification.SVM;
import smile.math.kernel.GaussianKernel;

import java.util.ArrayList;
import java.util.List;

/**
 * Destek Vektör Makinesi (SVM) sınıflandırma stratejisi (Gaussian kernel).
 * <p>
 * Her sınıf (AL, SAT, TUT) için ayrı bir ikili SVM modeli eğitir (one-vs-rest).
 * Tahmin aşamasında karar fonksiyonu değeri sigmoid ile {@code [0, 1]} aralığına
 * dönüştürülür ve en yüksek skorlu sınıf seçilir.
 * SMILE kütüphanesinin {@link SVM} sınıfını kullanır.
 */
public final class SvmStrategy implements ModelStrategy {
    private List<SVM<double[]>> binaries = new ArrayList<>();
    private int numClasses = 3;

    /**
     * Her sınıf için bir ikili SVM modeli eğitir (one-vs-rest).
     * Gaussian kernel (sigma=1.0) ve C=1.0 düzenleme parametresi kullanılır.
     *
     * @param features {@code double[N][11]} eğitim verisi
     * @param labels   {@code int[N]} etiketler (0=AL, 1=SAT, 2=TUT)
     */
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

    /**
     * Üç ikili SVM modelinin karar değerlerini sigmoid ile olasılığa dönüştürür
     * ve en yüksek skorlu sınıfı döndürür.
     *
     * @param features 11 boyutlu öznitelik vektörü
     * @return {@code [sınıf, skor]} — sınıf: 0=AL, 1=SAT, 2=TUT
     */
    @Override
    public synchronized double[] predict(double[] features) {
        if (binaries.isEmpty()) {
            return new double[]{0, 0.0};
        }
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
