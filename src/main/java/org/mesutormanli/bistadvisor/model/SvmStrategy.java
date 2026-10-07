package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.config.ModelType;
import smile.classification.SVM;
import smile.math.kernel.GaussianKernel;

import java.util.ArrayList;
import java.util.List;

/**
 * Destek Vektör Makinesi (SVM) sınıflandırma stratejisi (Gaussian kernel).
 * <p>
 * Eğitim etiketlerinde <strong>gözlenen</strong> her sınıf için ayrı bir ikili SVM modeli
 * eğitilir (one-vs-rest). Etiketler {@link ClassSpace} ile sıkı indekslere eşlenir; eğitim
 * setinde hiç oluşmayan sınıf için ikili model eğitilmez ve tahminde istisna fırlatılmaz
 * (eksik sınıf hiç tahmin edilmez). Tek sınıf varsa model eğitilmez, sabit tahmin döner.
 * <p>
 * Tahmin aşamasında karar fonksiyonu değeri sigmoid ile {@code [0, 1]} aralığına dönüştürülür
 * ve en yüksek skorlu sınıf seçilir. SMILE kütüphanesinin {@link SVM} sınıfını kullanır.
 */
public final class SvmStrategy implements ModelStrategy {
    private final List<SVM<double[]>> binaries = new ArrayList<>();
    private ClassSpace classes;

    /**
     * Gözlenen her sınıf için bir ikili SVM modeli eğitir (one-vs-rest).
     * Gaussian kernel (sigma=1.0) ve C=1.0 düzenleme parametresi kullanılır.
     *
     * @param features {@code double[N][11]} eğitim verisi
     * @param labels   {@code int[N]} etiketler (0=AL, 1=SAT, 2=TUT)
     */
    @Override
    public synchronized void train(double[][] features, int[] labels) {
        binaries.clear();
        classes = ClassSpace.of(labels);
        if (classes.size() == 1) return;
        for (int i = 0; i < classes.size(); i++) {
            int target = classes.label(i);
            int[] binary = new int[labels.length];
            for (int j = 0; j < labels.length; j++) binary[j] = (labels[j] == target) ? 1 : -1;
            binaries.add(SVM.fit(features, binary, new GaussianKernel(1.0),
                    new SVM.Options(1.0, 1E-3, 100)));
        }
    }

    /**
     * İkili SVM modellerinin karar değerlerini sigmoid ile olasılığa dönüştürür
     * ve en yüksek skorlu sınıfı döndürür.
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
        double bestScore = -1;
        int best = 0;
        for (int i = 0; i < binaries.size(); i++) {
            double decision = binaries.get(i).score(features);
            double score = 1.0 / (1.0 + Math.exp(-decision));
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return new double[]{classes.label(best), bestScore};
    }

    @Override
    public ModelType type() {
        return ModelType.SVM;
    }
}