package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.config.AnalysisType;
import org.mesutormanli.bistadvisor.config.ModelType;
import smile.classification.SVM;
import smile.math.kernel.GaussianKernel;

import java.util.ArrayList;
import java.util.List;

/**
 * Destek vektör makinesi (SVM) sınıflandırma stratejisi; one-vs-all yaklaşımı ve SMILE
 * {@link SVM} modelini kullanır.
 *
 * <p>Eğitimde etiketler {@link ClassSpace} ile sıkı indekslere sıkıştırılır; eğitim setinde hiç
 * oluşmayan sınıf için ikili model kurulmaz ve tahminde istisna fırlatılmaz. Gözlenen her sınıf
 * için ayrı bir ikili SVM eğitilir: hedef sınıf örnekleri {@code +1}, diğerleri {@code -1}
 * etiketlenir. İkili modeller Gaussian çekirdek (σ=1.0) ve
 * {@code SVM.Options(C=1.0, tol=1e-3, 100 iterasyon)} ile eğitilir. Tek sınıflı eğitimde model
 * kurmaz. Üye metodlar {@code synchronized} olduğundan sınıf thread-safe'tir.
 */
public final class SvmStrategy implements ModelStrategy {
    private final List<SVM<double[]>> binaries = new ArrayList<>();
    private ClassSpace classes;

    /**
     * Eğitir: gözlenen her sınıf için one-vs-all ikili SVM'leri eğitim seti üzerinde kurar.
     *
     * <p>Her ikili sınıflandırıcıda hedef sınıf örnekleri {@code +1}, diğerleri {@code -1}
     * olarak etiketlenir. Gaussian çekirdek (σ=1.0) kullanılır ve optimizasyon
     * {@code SVM.Options(C=1.0, tol=1e-3, 100 iterasyon)} ile yürütülür. Tek sınıf varsa
     * model kurulmaz.
     *
     * @param features eğitim seti öznitelik matrisi ({@code double[N][k]}; {@code k} =
     *                 {@code FeatureVector.dimension(type)})
     * @param labels   eğitim seti etiketleri ({@code int[N]}; 0=AL, 1=SAT, 2=TUT)
     * @param type     eğitimde kullanılan analiz tipi; matrisin sütun sayısını belirler
     */
    @Override
    public synchronized void train(double[][] features, int[] labels, AnalysisType type) {
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
     * Tahmin eder: ikili SVM karar skorlarına sigmoid uygulayıp en yüksek olasılıklı sınıfı
     * döndürür.
     *
     * <p>Her ikili sınıflandırıcının karar skoruna {@code 1/(1+e^-decision)} sigmoid dönüşümü
     * uygulanır; en yüksek olasılığa sahip sınıf ve olasılığı döndürülür. Eğitilmemişse
     * {@code {TUT, 0.0}}; tek sınıflı eğitimde {@code {o sınıf, 1.0}} döner.
     *
     * @param features analiz tipinin boyutunda öznitelik vektörü
     * @return 2 elemanlı dizi: {@code [sınıf etiketi, olasılık/skor]}
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

    /**
     * Bildirir: stratejinin {@link ModelType#SVM} türünde olduğunu döndürür.
     *
     * @return model türü ({@link ModelType#SVM})
     */
    @Override
    public ModelType type() {
        return ModelType.SVM;
    }
}