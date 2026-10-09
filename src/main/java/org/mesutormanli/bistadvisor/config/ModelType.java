package org.mesutormanli.bistadvisor.config;

/**
 * Uygulamanın desteklediği makine öğrenimi modeli türlerini tanımlar.
 * <ul>
 *   <li>{@code RANDOM_FOREST}: yapılandırma anahtarı {@code random_forest},
 *       görünen ad {@code RandomForest}.</li>
 *   <li>{@code SVM}: yapılandırma anahtarı {@code svm}, görünen ad {@code SVM}.</li>
 *   <li>{@code KNN}: yapılandırma anahtarı {@code knn}, görünen ad {@code KNN};
 *       skorları komşu oy payları hâlinde olduğu için eşik yorumlaması
 *       {@link org.mesutormanli.bistadvisor.advisor.ScoreGate} ile düzeltilir.</li>
 * </ul>
 */
public enum ModelType {
    RANDOM_FOREST("random_forest", "RandomForest"),
    SVM("svm", "SVM"),
    KNN("knn", "KNN");

    public final String key;

    public final String label;

    /**
     * Model türünü yapılandırma anahtarı ve görünen adıyla oluşturur.
     *
     * @param key   yapılandırmada kullanılan anahtar (örn. {@code random_forest})
     * @param label kullanıcı arayüzünde görünen ad (örn. {@code RandomForest})
     */
    ModelType(String key, String label) {
        this.key = key;
        this.label = label;
    }

    /**
     * Metin anahtarından uygun {@link ModelType} sabitini bulur.
     *
     * <p>Anahtar; enum anahtarı ({@code random_forest}, {@code svm}, {@code knn}),
     * enum adı ({@code RANDOM_FOREST}, {@code SVM}, {@code KNN}) ya da görünen ad
     * ({@code RandomForest}, {@code SVM}, {@code KNN}) ile büyük/küçük harfsiz
     * eşleştirilir.</p>
     *
     * @param key çözümlenecek model anahtarı
     * @return anahtarla eşleşen model türü; {@code key} {@code null} ise ya da eşleşme
     *         bulunamazsa varsayılan olarak {@link #RANDOM_FOREST}
     * @implNote Karşılaştırma {@link String#equalsIgnoreCase(String)} ile yapılır;
     *           dolayısıyla eşleşme büyük/küçük harfe duyarsızdır.
     */
    public static ModelType fromKey(String key) {
        if (key == null) return RANDOM_FOREST;
        for (ModelType t : values()) {
            if (t.key.equalsIgnoreCase(key) || t.name().equalsIgnoreCase(key) || t.label.equalsIgnoreCase(key)) return t;
        }
        return RANDOM_FOREST;
    }
}
