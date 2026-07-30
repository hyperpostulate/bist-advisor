package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.features.FeatureVector;

import java.util.List;

/**
 * Öznitelik vektörlerini ve etiketleri makine öğrenimi modellerinin
 * beklediği dizi formatlarına dönüştüren yardımcı sınıf.
 */
public final class FeatureFrame {

    private FeatureFrame() {}

    /**
     * Öznitelik adlarını döndürür (delege: {@link FeatureVector#featureNames()}).
     *
     * @return öznitelik isimleri dizisi
     */
    public static String[] names() { return FeatureVector.featureNames(); }

    /**
     * {@link FeatureVector} listesini {@code double[N][11]} matrisine dönüştürür.
     *
     * @param features öznitelik vektörleri listesi
     * @return eğitim matrisi
     */
    public static double[][] toMatrix(List<FeatureVector> features) {
        double[][] m = new double[features.size()][];
        for (int i = 0; i < features.size(); i++) m[i] = features.get(i).toArray();
        return m;
    }

    /**
     * {@link Integer} listesini {@code int[]} dizisine dönüştürür.
     *
     * @param labels etiket listesi
     * @return etiket dizisi
     */
    public static int[] toLabels(List<Integer> labels) {
        return labels.stream().mapToInt(Integer::intValue).toArray();
    }
}
