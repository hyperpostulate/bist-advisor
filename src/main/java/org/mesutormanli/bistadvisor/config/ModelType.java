package org.mesutormanli.bistadvisor.config;

/**
 * Desteklenen makine öğrenimi modeli türlerini tanımlar.
 * <ul>
 *   <li>{@code RANDOM_FOREST} — Random Forest (one-vs-rest)</li>
 *   <li>{@code SVM} — Support Vector Machine (Gaussian kernel)</li>
 *   <li>{@code KNN} — k-En Yakın Komşu (k=5)</li>
 * </ul>
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

    /**
     * Metin anahtarına ({@code "random_forest"}, {@code "svm"}, {@code "knn"}) ya da
     * enum adına/etiketine göre uygun {@code ModelType} değerini döndürür.
     * Eşleşme bulunamazsa varsayılan olarak {@code RANDOM_FOREST} döner.
     *
     * @param key model anahtarı (case-insensitive)
     * @return eşleşen {@code ModelType} sabiti
     */
    public static ModelType fromKey(String key) {
        if (key == null) return RANDOM_FOREST;
        for (ModelType t : values()) {
            if (t.key.equalsIgnoreCase(key) || t.name().equalsIgnoreCase(key) || t.label.equalsIgnoreCase(key)) return t;
        }
        return RANDOM_FOREST;
    }
}
