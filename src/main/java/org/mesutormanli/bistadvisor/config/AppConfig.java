package org.mesutormanli.bistadvisor.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Uygulama genelindeki yapılandırma parametrelerini {@code application.properties}
 * üzerinden {@code @Value} ile bağlayan Spring bileşeni.
 * <p>Yapılandırılan parametreler ve varsayılanları şunlardır:</p>
 * <ul>
 *   <li>{@code bist.ml.model}: varsayılan model anahtarı (varsayılan
 *       {@code random_forest}); bkz. {@link #defaultModelType()}.</li>
 *   <li>{@code bist.data.cache-dir}: fiyat serisi önbellek dizini (varsayılan
 *       {@code cache}).</li>
 *   <li>{@code bist.data.state-file}: portföy durumu dosyası (varsayılan
 *       {@code state.yaml}).</li>
 *   <li>{@code bist.model.label-horizon-days}: etiketleme ufku, gün (varsayılan
 *       {@code 20}).</li>
 *   <li>{@code bist.scrape.timeout-ms}: veri çekme isteği zaman aşımı, ms
 *       (varsayılan {@code 15000}).</li>
 *   <li>{@code bist.scrape.delay-ms}: veri çekme istekleri arası gecikme, ms
 *       (varsayılan {@code 250}).</li>
 * </ul>
 *
 * @implNote Erişimci metotlar değiştirilemez (immutable) bir görünüm sunar;
 *           alanların kendileri yalnızca Spring bağlaması sırasında yazılır.
 */
@Component
public class AppConfig {

    @Value("${bist.ml.model:random_forest}")
    private String defaultModelKey;

    @Value("${bist.data.cache-dir:cache}")
    private String cacheDir;

    @Value("${bist.data.state-file:state.yaml}")
    private String stateFile;

    @Value("${bist.model.label-horizon-days:20}")
    private int labelHorizonDays;

    @Value("${bist.scrape.timeout-ms:15000}")
    private int scrapeTimeoutMs;

    @Value("${bist.scrape.delay-ms:250}")
    private int scrapeDelayMs;

    /**
     * Yapılandırılan varsayılan model türünü çözer.
     *
     * @return {@code bist.ml.model} anahtarından elde edilen {@link ModelType};
     *         bilinmeyen bir değer verilirse {@link ModelType#RANDOM_FOREST}
     * @implNote Çözümleme {@link ModelType#fromKey(String)} ile yapılır; bu yöntem
     *           {@code null} veya tanınmayan anahtarlar için {@code RANDOM_FOREST}
     *           döndürür.
     */
    public ModelType defaultModelType() {
        return ModelType.fromKey(defaultModelKey);
    }

    /**
     * Fiyat serisi önbellek dizininin yolunu döndürür.
     *
     * @return önbellek dizini yolu (varsayılan {@code cache})
     */
    public String cacheDir() { return cacheDir; }

    /**
     * Portföy durumu dosyasının yolunu döndürür.
     *
     * @return durum dosyası yolu (varsayılan {@code state.yaml})
     */
    public String stateFile() { return stateFile; }

    /**
     * Etiketleme ufkunu (getirinin hesaplandığı ileri gün sayısı) döndürür.
     *
     * @return etiketleme ufkunun gün cinsinden değeri (varsayılan {@code 20})
     */
    public int labelHorizonDays() { return labelHorizonDays; }

    /**
     * Veri çekme isteklerindeki zaman aşımı süresini döndürür.
     *
     * @return zaman aşımının milisaniye cinsinden değeri (varsayılan {@code 15000})
     */
    public int scrapeTimeoutMs() { return scrapeTimeoutMs; }

    /**
     * Ardışık veri çekme istekleri arasındaki gecikmeyi döndürür.
     *
     * @return gecikmenin milisaniye cinsinden değeri (varsayılan {@code 250})
     */
    public int scrapeDelayMs() { return scrapeDelayMs; }
}
