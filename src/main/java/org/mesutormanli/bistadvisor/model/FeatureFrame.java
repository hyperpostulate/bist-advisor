package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.config.AnalysisType;
import org.mesutormanli.bistadvisor.features.FeatureVector;

import java.util.List;

/**
 * Köprü/yardımcı sınıf: {@link FeatureVector} listelerini ve etiket listelerini SMILE
 * modellerinin beklediği dizi formatlarına dönüştürür ve öznitelik kolon adlarını sağlar.
 *
 * <p>SMILE {@code DataFrame} kolon adları {@link FeatureVector#featureNames(AnalysisType)}
 * ile aynıdır ve seçilen analiz tipinin metrik kümesini yansıtır (6/5/11 sütun); bu sayede
 * rastgele orman gibi formül tabanlı modeller öznitelikleri adıyla adresleyebilir. Tüm
 * üyeler statiktir, sınıf örneklenemez.
 */
public final class FeatureFrame {

    /**
     * Kurar: yardımcı sınıfın örneklenmesini engelleyen özel yapıcı.
     */
    private FeatureFrame() {}

    /**
     * Döndürür: analiz tipinin öznitelik kolon adlarını verir (SMILE kolon adları).
     *
     * @param type analiz tipi
     * @return analiz tipinin metrik kümesine karşılık gelen kolon isimleri dizisi
     */
    public static String[] names(AnalysisType type) { return FeatureVector.featureNames(type); }

    /**
     * Dönüştürür: {@link FeatureVector} listesini eğitim matrisine çevirir.
     *
     * @param features öznitelik vektörleri listesi
     * @param type     analiz tipi; her satırın sütun düzenini ve genişliğini belirler
     * @return özellik matrisi ({@code double[N][FeatureVector.dimension(type)]})
     */
    public static double[][] toMatrix(List<FeatureVector> features, AnalysisType type) {
        double[][] m = new double[features.size()][];
        for (int i = 0; i < features.size(); i++) m[i] = features.get(i).toArray(type);
        return m;
    }

    /**
     * Dönüştürür: {@link Integer} etiket listesini ilkel diziye çevirir.
     *
     * @param labels etiket listesi
     * @return etiket dizisi ({@code int[N]})
     */
    public static int[] toLabels(List<Integer> labels) {
        return labels.stream().mapToInt(Integer::intValue).toArray();
    }
}
