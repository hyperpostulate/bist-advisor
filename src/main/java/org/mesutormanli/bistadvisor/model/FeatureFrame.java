package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.features.FeatureVector;

import java.util.List;

/**
 * Köprü/yardımcı sınıf: {@link FeatureVector} listelerini ve etiket listelerini SMILE
 * modellerinin beklediği dizi formatlarına dönüştürür ve öznitelik kolon adlarını sağlar.
 *
 * <p>SMILE {@code DataFrame} kolon adları {@link FeatureVector#featureNames()} ile aynıdır
 * (11 öznitelik); bu sayede rastgele orman gibi formül tabanlı modeller öznitelikleri adıyla
 * adresleyebilir. Tüm üyeler statiktir, sınıf örneklenemez.
 */
public final class FeatureFrame {

    /**
     * Kurar: yardımcı sınıfın örneklenmesini engelleyen özel yapıcı.
     */
    private FeatureFrame() {}

    /**
     * Döndürür: öznitelik kolon adlarını verir (SMILE kolon adları).
     *
     * @return 11 boyutlu öznitelik isimleri dizisi
     */
    public static String[] names() { return FeatureVector.featureNames(); }

    /**
     * Dönüştürür: {@link FeatureVector} listesini eğitim matrisine çevirir.
     *
     * @param features öznitelik vektörleri listesi
     * @return özellik matrisi ({@code double[N][11]})
     */
    public static double[][] toMatrix(List<FeatureVector> features) {
        double[][] m = new double[features.size()][];
        for (int i = 0; i < features.size(); i++) m[i] = features.get(i).toArray();
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
