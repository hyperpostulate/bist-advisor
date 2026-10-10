package org.mesutormanli.bistadvisor.application;

import org.mesutormanli.bistadvisor.domain.ml.ModelStrategy;
import org.mesutormanli.bistadvisor.domain.settings.AnalysisType;
import org.mesutormanli.bistadvisor.domain.settings.ModelType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Eğitilmiş modelleri bellekte saklayan önbellek (TİP:ANALİZ:ENDEKS anahtarlı).
 * <p>
 * Modeller diske kaydedilmez, yalnızca çalışma süresince tutulur. Anahtar analiz
 * tipini de taşıdığı için farklı metrik kümeleriyle eğitilmiş modeller birbirine
 * karışmaz; boyut uyuşmazlığı bu sayede imkânsızdır. Erişim metodları senkrondur.
 */
@Component
public class ModelRegistry {

    private final Map<String, ModelStrategy> models = new HashMap<>();

    /**
     * Önbellek anahtarı hesaplar.
     *
     * @param type         model türü
     * @param analysisType analiz tipi (metrik kümesi)
     * @param indexName    endeks adı (büyük harfe çevrilir)
     * @return {@code "TİP:ANALİZ:ENDEKS"} biçiminde önbellek anahtarı
     */
    static String keyFor(ModelType type, AnalysisType analysisType, String indexName) {
        return type.name() + ":" + analysisType.name() + ":"
                + (indexName != null ? indexName.toUpperCase() : "");
    }

    /**
     * Önbellekteki modeli döndürür.
     *
     * @param type         model türü
     * @param analysisType analiz tipi
     * @param indexName    endeks adı
     * @return kayıtlı model; yoksa {@code null}
     */
    public synchronized ModelStrategy get(ModelType type, AnalysisType analysisType, String indexName) {
        return models.get(keyFor(type, analysisType, indexName));
    }

    /**
     * Modeli önbelleğe alır.
     *
     * @param type         model türü
     * @param analysisType analiz tipi
     * @param indexName    endeks adı
     * @param model        eğitilmiş model
     */
    public synchronized void put(ModelType type, AnalysisType analysisType,
                                 String indexName, ModelStrategy model) {
        models.put(keyFor(type, analysisType, indexName), model);
    }
}