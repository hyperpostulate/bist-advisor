package org.mesutormanli.bistadvisor.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Uygulama genelindeki yapılandırma parametrelerini {@code application.properties}
 * dosyasından okuyarak sağlayan Spring bean'i.
 * <p>
 * Varsayılan ML modeli, önbellek dizini, state dosya yolu, etiketleme ufku ve
 * HTTP istek zaman aşımı/gecikme değerlerini içerir.
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
     * Varsayılan ML model türünü döndürür.
     *
     * @return {@code application.properties} üzerinden belirlenen model türü
     */
    public ModelType defaultModelType() {
        return ModelType.fromKey(defaultModelKey);
    }

    /**
     * Önbellek dosyalarının saklandığı dizin yolunu döndürür.
     */
    public String cacheDir() { return cacheDir; }

    /**
     * Portföy durumunun kaydedildiği YAML dosyasının yolunu döndürür.
     */
    public String stateFile() { return stateFile; }

    /**
     * Etiketleme (labeling) için kullanılan getiri hesaplama ufkunu (gün) döndürür.
     */
    public int labelHorizonDays() { return labelHorizonDays; }

    /**
     * Yahoo Finance API isteklerinde kullanılan zaman aşımı süresini (ms) döndürür.
     */
    public int scrapeTimeoutMs() { return scrapeTimeoutMs; }

    /**
     * Yahoo Finance API ardışık istekleri arasındaki gecikmeyi (ms) döndürür.
     */
    public int scrapeDelayMs() { return scrapeDelayMs; }
}
