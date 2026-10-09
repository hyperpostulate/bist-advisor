package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.config.AnalysisType;
import org.mesutormanli.bistadvisor.config.ModelType;
import smile.classification.RandomForest;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.type.StructType;

import java.util.ArrayList;
import java.util.List;

/**
 * Rastgele orman (Random Forest) sınıflandırma stratejisi; one-vs-all yaklaşımı ve SMILE
 * {@link RandomForest} modelini kullanır.
 *
 * <p>Eğitimde etiketler {@link ClassSpace} ile sıkı indekslere sıkıştırılır; eğitim setinde hiç
 * oluşmayan sınıf için ikili orman kurulmaz ve tahminde istisna fırlatılmaz. Gözlenen her sınıf
 * için ayrı bir ikili rastgele orman eğitilir: hedef kolon adı {@code sinif} olup hedef sınıf
 * örnekleri {@code 1}, diğerleri {@code 0} ile etiketlenir. Modeller SMILE {@code DataFrame} ve
 * {@code Formula.lhs("sinif")} ile kurulur; öznitelik kolon adları
 * {@link FeatureFrame#names(AnalysisType)} kolon adları ile sağlanır (analiz tipi 6/5/11
 * sütun belirler). Tek sınıflı eğitimde model kurmaz.
 * Üye metodlar {@code synchronized} olduğundan sınıf thread-safe'tir.
 */
public final class RandomForestStrategy implements ModelStrategy {
    private final List<RandomForest> forests = new ArrayList<>();

    private StructType schema;

    private ClassSpace classes;

    /**
     * Eğitir: gözlenen her sınıf için one-vs-all ikili rastgele ormanları eğitim seti üzerinde
     * kurar.
     *
     * <p>Her ikili modelde hedef kolon {@code sinif} adıyla eklenir ve hedef sınıf örnekleri
     * {@code 1}, diğerleri {@code 0} ile etiketlenir; ormanlar
     * {@code Formula.lhs("sinif")} ile eğitilir. Ayrıca tahmin için yalnızca öznitelik
     * kolonlarını içeren şema saklanır. Tek sınıf varsa model kurulmaz.
     *
     * @param features eğitim seti öznitelik matrisi ({@code double[N][k]}; {@code k} =
     *                 {@code FeatureVector.dimension(type)})
     * @param labels   eğitim seti etiketleri ({@code int[N]}; 0=AL, 1=SAT, 2=TUT)
     * @param type     eğitimde kullanılan analiz tipi; kolon adlarını ve sütun sayısını belirler
     */
    @Override
    public synchronized void train(double[][] features, int[] labels, AnalysisType type) {
        forests.clear();
        classes = ClassSpace.of(labels);
        String[] names = FeatureFrame.names(type);
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
     * Tahmin eder: ikili ormanların pozitif sınıf olasılıklarını karşılaştırıp en yüksek
     * olasılıklı sınıfı döndürür.
     *
     * <p>Öznitelik vektörü şemayla bir {@code Tuple} olarak paketlenir; her ikili ormandan
     * 2 sınıf olasılığı alınır ve pozitif sınıf olasılığı ({@code prob[1]}) en yüksek olan
     * sınıf kazanır. Eğitilmemişse {@code {TUT, 0.0}}; tek sınıflı eğitimde
     * {@code {o sınıf, 1.0}} döner.
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

    /**
     * Bildirir: stratejinin {@link ModelType#RANDOM_FOREST} türünde olduğunu döndürür.
     *
     * @return model türü ({@link ModelType#RANDOM_FOREST})
     */
    @Override
    public ModelType type() { return ModelType.RANDOM_FOREST; }
}