package org.mesutormanli.bistadvisor.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** application.properties'den okunan uygulama geneli konfigurasyon degerleri. */
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

    /** Varsayilan ML model tipi (application.properties -> bist.ml.model). */
    public ModelType defaultModelType() {
        return ModelType.fromKey(defaultModelKey);
    }

    /** Fiyat serisi onbellek dizini (varsayilan: cache/). */
    public String cacheDir() { return cacheDir; }

    /** Portfoy durumu dosyasi (varsayilan: state.yaml). */
    public String stateFile() { return stateFile; }

    /** Etiketleme icin N gunluk getiri hesaplama ufku (varsayilan: 20). */
    public int labelHorizonDays() { return labelHorizonDays; }

    /** Yahoo API istek zamani asimi (ms). */
    public int scrapeTimeoutMs() { return scrapeTimeoutMs; }

    /** Yahoo API istekler arasi gecikme (ms). */
    public int scrapeDelayMs() { return scrapeDelayMs; }
}
