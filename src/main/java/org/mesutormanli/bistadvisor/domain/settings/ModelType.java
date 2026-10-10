package org.mesutormanli.bistadvisor.domain.settings;

/**
 * Makine öğrenmesi model türü.
 * <p>
 * Sabitler SMILE tabanlı üç sınıflandırıcıya karşılık gelir. {@link #fromKey(String)}
 * anahtar/enum adı/görünen ad eşleşmesini büyük/küçük harfsiz yapar ve tanınmayan
 * girdilerde {@link #RANDOM_FOREST} varsaylanır.
 */
public enum ModelType {
    RANDOM_FOREST("random_forest", "RandomForest"),
    SVM("svm", "SVM"),
    KNN("knn", "KNN");

    /** Yapılandırmada kullanılan anahtar (örn. {@code random_forest}). */
    public final String key;

    /** Arayüzlerde görünen ad. */
    public final String label;

    ModelType(String key, String label) {
        this.key = key;
        this.label = label;
    }

    /**
     * Metin anahtarından uygun {@link ModelType} sabitini bulur.
     *
     * @param key çözümlenecek model anahtarı
     * @return anahtarla eşleşen model türü; eşleşme yoksa {@link #RANDOM_FOREST}
     */
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