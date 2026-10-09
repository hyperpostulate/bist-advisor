package org.mesutormanli.bistadvisor.config;

/**
 * Analizde kullanılacak metrik (öznitelik) kümesini belirleyen analiz tipi.
 * <p>
 * Model girdisi 11 boyuttan oluşur: 6 teknik gösterge
 * ({@code rsi}, {@code sma20Ratio}, {@code sma50Ratio}, {@code macd}, {@code volatility},
 * {@code volumeRatio}) ve 5 temel gösterge ({@code fk}, {@code pdDd}, {@code dividendYield},
 * {@code profitGrowth}, {@code roi}). Analiz tipi bu iki grubun hangilerinin hem eğitimde
 * hem tahminde kullanılacağını belirler:</p>
 * <ul>
 *   <li>{@code TECHNICAL} (TEKNIK): yalnızca teknik göstergeler kullanılır; temel veriye
 *       hiç başvurulmadığı için güncel tarihli temel göstergelerin geçmiş örneklere
 *       uygulanmasından doğan geleceğe sızıntı (look-ahead bias) bu modda yaşanmaz.</li>
 *   <li>{@code FUNDAMENTAL} (TEMEL): yalnızca temel göstergeler kullanılır.</li>
 *   <li>{@code TECHNICAL_FUNDAMENTAL} (TEKNIK_TEMEL): tüm 11 metrik birlikte kullanılır.</li>
 * </ul>
 *
 * <p>Seçim {@code state.yaml} içindeki {@code analysisType} alanında saklanır ve eğitim
 * matrisi ile tahmin vektörünün boyutlarını birlikte belirler; bu yüzden aynı tipte
 * eğitilmemiş bir modelde tahmin yapılması teknik olarak imkânsızdır
 * (bkz. {@code ModelTrainer} önbellek anahtarı).</p>
 */
public enum AnalysisType {
    TECHNICAL("technical", "TEKNIK"),
    FUNDAMENTAL("fundamental", "TEMEL"),
    TECHNICAL_FUNDAMENTAL("technical_fundamental", "TEKNIK_TEMEL");

    public final String key;

    public final String label;

    /**
     * Analiz tipini yapılandırma anahtarı ve görünen adıyla oluşturur.
     *
     * @param key   yapılandırmada kullanılan anahtar (örn. {@code technical})
     * @param label CLI ve arayüzlerde görünen Türkçe ad (örn. {@code TEKNIK})
     */
    AnalysisType(String key, String label) {
        this.key = key;
        this.label = label;
    }

    /**
     * Döndürür: bu analiz tipinin teknik göstergeleri kullanıp kullanmadığını bildirir.
     *
     * @return teknik göstergeler kullanılıyorsa {@code true}, aksi hâlde {@code false}
     */
    public boolean usesTechnical() {
        return this != FUNDAMENTAL;
    }

    /**
     * Döndürür: bu analiz tipinin temel göstergeleri kullanıp kullanmadığını bildirir.
     *
     * @return temel göstergeler kullanılıyorsa {@code true}, aksi hâlde {@code false}
     */
    public boolean usesFundamental() {
        return this != TECHNICAL;
    }

    /**
     * Çözer: metin anahtarından uygun {@link AnalysisType} sabitini bulur.
     *
     * <p>Anahtar; enum anahtarı ({@code technical}, {@code fundamental},
     * {@code technical_fundamental}), enum adı ({@code TECHNICAL}, {@code FUNDAMENTAL},
     * {@code TECHNICAL_FUNDAMENTAL}) ya da görünen ad ({@code TEKNIK}, {@code TEMEL},
     * {@code TEKNIK_TEMEL}) ile büyük/küçük harfsiz eşleştirilir. Kullanıcı dostu birkaç
     * takma ad da kabul edilir: {@code YALNIZCA_TEKNIK} → {@link #TECHNICAL},
     * {@code YALNIZCA_TEMEL} → {@link #FUNDAMENTAL}; {@code TEKNIK_VE_TEMEL},
     * {@code TEKNIK+TEMEL} ve {@code TUMU} → {@link #TECHNICAL_FUNDAMENTAL}.
     *
     * @param key çözümlenecek analiz tipi anahtarı
     * @return anahtarla eşleşen analiz tipi; {@code key} {@code null} ise ya da eşleşme
     *         bulunamazsa varsayılan olarak {@link #TECHNICAL_FUNDAMENTAL}
     * @implNote Karşılaştırma {@link String#equalsIgnoreCase(String)} ile yapılır;
     *           dolayısıyla eşleşme büyük/küçük harfe duyarsızdır. Takma adlarda boşluklar
     *           alt çizgiye çevrilerek de denenir.
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