package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.features.FeatureVector;

import java.util.List;

/**
 * Ozellik vektorlerini ML modellerine beslenecek matrise cevirir.
 * Tum modeller double[][]] (satir = ornek, sutun = ozellik) ve int[] etiket kullanir.
 */
public final class FeatureFrame {

    private FeatureFrame() {
    }

    public static String[] names() {
        return FeatureVector.featureNames();
    }

    /** Ozellik matrisi: satir = ornek, sutun = ozellik. */
    public static double[][] toMatrix(List<FeatureVector> features) {
        double[][] m = new double[features.size()][];
        for (int i = 0; i < features.size(); i++) {
            m[i] = features.get(i).toArray();
        }
        return m;
    }

    public static int[] toLabels(List<Integer> labels) {
        int[] a = new int[labels.size()];
        for (int i = 0; i < labels.size(); i++) a[i] = labels.get(i);
        return a;
    }
}
