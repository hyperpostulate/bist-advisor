package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.features.FeatureVector;

import java.util.List;

/** FeatureVector listesini ML modellerinin bekledigi double[][] matrise cevirir. */
public final class FeatureFrame {

    private FeatureFrame() {}

    /** 11 ozellik adini dondurur (FeatureVector.featureNames()'e yonlendirir). */
    public static String[] names() { return FeatureVector.featureNames(); }

    /** FeatureVector listesini satir = ornek, sutun = ozellik olacak sekilde double[][]'e cevirir. */
    public static double[][] toMatrix(List<FeatureVector> features) {
        double[][] m = new double[features.size()][];
        for (int i = 0; i < features.size(); i++) m[i] = features.get(i).toArray();
        return m;
    }

    /** Integer etiket listesini int[] dizisine cevirir. */
    public static int[] toLabels(List<Integer> labels) {
        int[] a = new int[labels.size()];
        for (int i = 0; i < labels.size(); i++) a[i] = labels.get(i);
        return a;
    }
}
