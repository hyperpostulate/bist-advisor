package org.mesutormanli.bistadvisor.application;

import org.mesutormanli.bistadvisor.adapters.indices.BistIndices;
import org.mesutormanli.bistadvisor.config.AppConfig;
import org.mesutormanli.bistadvisor.domain.ml.Labeler;
import org.mesutormanli.bistadvisor.domain.ml.ModelStrategy;
import org.mesutormanli.bistadvisor.domain.ml.ModelStrategyFactory;
import org.mesutormanli.bistadvisor.domain.ml.TrainingSet;
import org.mesutormanli.bistadvisor.domain.settings.AnalysisType;
import org.mesutormanli.bistadvisor.domain.settings.ModelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sınıflandırma modellerinin yaşam döngüsünden sorumlu servis: canlı veriden
 * eğitim seti kurar, modeli eğitir ve {@link ModelRegistry} içinde tutar.
 * <p>
 * {@link #getOrTrain(ModelType, AnalysisType, String)} önbellekten döndürür ya
 * da kurar/eğitir/önbelleğe alar; {@link #train(ModelType, AnalysisType, String)}
 * her zaman yeniden eğitir. Her iki metot da senkrondur. Eğitim seti hiç
 * üretilemezse {@link IllegalStateException} fırlatılır ve AL/SAT/TUT sınıf
 * dağılımı günlüğe raporlanır (eksik sınıf varsa uyarı da basılır).
 */
@Service
public class ModelTrainingService {

    private static final Logger log = LoggerFactory.getLogger(ModelTrainingService.class);

    private final ModelRegistry registry;
    private final TrainingSetBuilder trainingSets;
    private final BistIndices bistIndices;
    private final AppConfig appConfig;

    /**
     * Kurar: model önbelleği, eğitim seti kurucusu, endeks bilgisi ve
     * yapılandırmayı enjekte eder.
     *
     * @param registry     eğitilmiş model önbelleği
     * @param trainingSets eğitim seti kurucusu
     * @param bistIndices  endeks-sembol eşlemelerini sağlayan katalog
     * @param appConfig    etiket ufku gibi model ayarları
     */
    public ModelTrainingService(ModelRegistry registry, TrainingSetBuilder trainingSets,
                                BistIndices bistIndices, AppConfig appConfig) {
        this.registry = registry;
        this.trainingSets = trainingSets;
        this.bistIndices = bistIndices;
        this.appConfig = appConfig;
    }

    /**
     * Önbellekteki modeli döndürür; yoksa kurar, eğitir ve önbelleğe alar.
     *
     * @param type         model türü
     * @param analysisType analiz tipi (metrik kümesi)
     * @param indexName    endeks adı
     * @return eğitilmiş model (önbellekte varsa o döndürülür)
     * @throws IllegalStateException eğitim verisi hiç üretilemezse
     */
    public synchronized ModelStrategy getOrTrain(ModelType type, AnalysisType analysisType, String indexName) {
        ModelStrategy cached = registry.get(type, analysisType, indexName);
        return cached != null ? cached : train(type, analysisType, indexName);
    }

    /**
     * Modeli her zaman yeniden kurar ve önbelleği günceller.
     *
     * @param type         model türü
     * @param analysisType analiz tipi (metrik kümesi)
     * @param indexName    endeks adı
     * @return yeni eğitilmiş model
     * @throws IllegalStateException eğitim verisi hiç üretilemezse
     */
    public synchronized ModelStrategy train(ModelType type, AnalysisType analysisType, String indexName) {
        TrainingSet ts = trainingSets.build(bistIndices.symbolsOf(indexName), analysisType,
                appConfig.labelHorizonDays());
        if (ts.labels().length == 0) {
            throw new IllegalStateException("canli egitim verisi uretilemedi (endeks: " + indexName + ")");
        }
        ModelStrategy strategy = ModelStrategyFactory.create(type);
        strategy.train(ts.features(), ts.labels(), analysisType);
        registry.put(type, analysisType, indexName, strategy);
        logClassDistribution(ts.labels());
        log.info("Model egitildi (bellekte): {}:{}:{} (ornek={})",
                type, analysisType, indexName, ts.labels().length);
        return strategy;
    }

    /**
     * Eğitim setindeki AL/SAT/TUT sınıf dağılımını raporlar.
     *
     * <p>Bir sınıf hiç oluşmamışsa uyarı basar: modeller eksik sınıfı üretemez,
     * bu yüzden karar eşikleri esnekleştirilmelidir.
     *
     * @param y etiket dizisi ({@code int[N]})
     */
    private void logClassDistribution(int[] y) {
        int buy = 0, sell = 0, hold = 0;
        for (int label : y) {
            switch (label) {
                case Labeler.BUY -> buy++;
                case Labeler.SELL -> sell++;
                default -> hold++;
            }
        }
        log.info("Egitim seti: {} ornek (AL={}, SAT={}, TUT={})", y.length, buy, sell, hold);
        if (buy == 0 || sell == 0 || hold == 0) {
            log.warn("Egitim setinde bazi siniflar hic olusmadi (AL={}, SAT={}, TUT={}); "
                    + "modeller eksik sinifi uretemez, esikler esneklesmeli", buy, sell, hold);
        }
    }
}