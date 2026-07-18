package org.mesutormanli.bistadvisor.config;

/**
 * Desteklenen ML modelleri. application.properties (bist.ml.model) veya
 * web arayuzunden (state.yaml modelType) konfigure edilir.
 */
public enum ModelType {
    RANDOM_FOREST("random_forest", "RandomForest"),
    SVM("svm", "SVM"),
    KNN("knn", "KNN");

    public final String key;
    public final String label;

    ModelType(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public static ModelType fromKey(String key) {
        if (key == null) return RANDOM_FOREST;
        for (ModelType t : values()) {
            if (t.key.equalsIgnoreCase(key) || t.name().equalsIgnoreCase(key) || t.label.equalsIgnoreCase(key)) {
                return t;
            }
        }
        return RANDOM_FOREST;
    }
}
