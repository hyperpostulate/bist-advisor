package org.mesutormanli.bistadvisor.config;

/** Desteklenen ML modelleri: RandomForest, SVM, KNN (SMILE 6.2.3). */
public enum ModelType {
    RANDOM_FOREST("random_forest", "RandomForest"),
    SVM("svm", "SVM"),
    KNN("knn", "KNN");

    /** application.properties / state.yaml'de kullanilan anahtar. */
    public final String key;
    /** Kullaniciya gosterilen etiket. */
    public final String label;

    ModelType(String key, String label) {
        this.key = key;
        this.label = label;
    }

    /** key/name/label ile eslesen model tipini dondurur, eslesmezse RANDOM_FOREST. */
    public static ModelType fromKey(String key) {
        if (key == null) return RANDOM_FOREST;
        for (ModelType t : values()) {
            if (t.key.equalsIgnoreCase(key) || t.name().equalsIgnoreCase(key) || t.label.equalsIgnoreCase(key)) return t;
        }
        return RANDOM_FOREST;
    }
}
