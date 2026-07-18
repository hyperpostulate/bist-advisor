package org.mesutormanli.bistadvisor.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Uygulama geneli konfigurasyon: varsayilan ML modeli, BIST-30 listesi,
 * egitim parametreleri ve dosya yollari.
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
    private int scrapeTimeoutMs = 15000;

    @Value("${bist.scrape.delay-ms:250}")
    private int scrapeDelayMs = 250;

    public ModelType defaultModelType() {
        return ModelType.fromKey(defaultModelKey);
    }

    public String cacheDir() {
        return cacheDir;
    }

    public String stateFile() {
        return stateFile;
    }

    public int labelHorizonDays() {
        return labelHorizonDays;
    }

    public int scrapeTimeoutMs() {
        return scrapeTimeoutMs;
    }

    public int scrapeDelayMs() {
        return scrapeDelayMs;
    }
}
