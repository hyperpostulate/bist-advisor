package org.mesutormanli.bistadvisor.domain.settings;

/**
 * Analizde kullanılacak metrik (öznitelik) kümesini belirleyen analiz tipi.
 * <p>
 * Model girdisi 11 boyuttan oluşur: 6 teknik gösterge ve 5 temel gösterge.
 * Analiz tipi bu iki grubun hangilerinin hem eğitimde hem tahminde
 * kullanılacağını belirler: {@code TECHNICAL} yalnız teknik (temel veriye hiç
 * başvurulmaz, dolayısıyla look-ahead bias yaşanmaz), {@code FUNDAMENTAL} yalnız
 * temel, {@code TECHNICAL_FUNDAMENTAL} tüm 11 metrik. Seçim eğitim matrisi ile
 * tahmin vektörünün boyutlarını birlikte belirler.
 */
public enum AnalysisType {
    TECHNICAL("technical", "TEKNIK"),
    FUNDAMENTAL("fundamental", "TEMEL"),
    TECHNICAL_FUNDAMENTAL("technical_fundamental", "TEKNIK_TEMEL");

    /** Yapılandırmada kullanılan anahtar (örn. {@code technical}). */
    public final String key;

    /** Arayüzlerde görünen Türkçe ad (örn. {@code TEKNIK}). */
    public final String label;

    AnalysisType(String key, String label) {
        this.key = key;
        this.label = label;
    }

    /**
     * Bu analiz tipinin teknik göstergeleri kullanıp kullanmadığını bildirir.
     *
     * @return teknik göstergeler kullanılıyorsa {@code true}
     */
    public boolean usesTechnical() {
        return this != FUNDAMENTAL;
    }

    /**
     * Bu analiz tipinin temel göstergeleri kullanıp kullanmadığını bildirir.
     *
     * @return temel göstergeler kullanılıyorsa {@code true}
     */
    public boolean usesFundamental() {
        return this != TECHNICAL;
    }

    /**
     * Metin anahtarından uygun {@link AnalysisType} sabitini bulur.
     * <p>
     * Anahtar; enum anahtarı, enum adı ya da görünen ad ile büyük/küçük harfsiz
     * eşleştirilir. Takma adlar da kabul edilir: {@code YALNIZCA_TEKNIK} →
     * {@link #TECHNICAL}, {@code YALNIZCA_TEMEL} → {@link #FUNDAMENTAL};
     * {@code TEKNIK_VE_TEMEL}, {@code TEKNIK+TEMEL} ve {@code TUMU} →
     * {@link #TECHNICAL_FUNDAMENTAL}.
     *
     * @param key çözümlenecek analiz tipi anahtarı
     * @return anahtarla eşleşen analiz tipi; eşleşme yoksa
     *         {@link #TECHNICAL_FUNDAMENTAL}
     */
    public static AnalysisType fromKey(String key) {
        if (key == null) return TECHNICAL_FUNDAMENTAL;
        for (AnalysisType t : values()) {
            if (t.key.equalsIgnoreCase(key) || t.name().equalsIgnoreCase(key) || t.label.equalsIgnoreCase(key)) {
                return t;
            }
        }
        String alias = key.trim().toUpperCase().replace(' ', '_');
        return switch (alias) {
            case "YALNIZCA_TEKNIK" -> TECHNICAL;
            case "YALNIZCA_TEMEL" -> FUNDAMENTAL;
            case "TEKNIK_VE_TEMEL", "TEKNIK+TEMEL", "TUMU" -> TECHNICAL_FUNDAMENTAL;
            default -> TECHNICAL_FUNDAMENTAL;
        };
    }
}