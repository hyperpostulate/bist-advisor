package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.config.AppConfig;
import org.mesutormanli.bistadvisor.config.ModelType;
import org.mesutormanli.bistadvisor.data.BistIndices;
import org.mesutormanli.bistadvisor.data.CacheStore;
import org.mesutormanli.bistadvisor.data.YahooClient;
import org.mesutormanli.bistadvisor.data.YahooClient.Fundamentals;
import org.mesutormanli.bistadvisor.features.FeatureVector;
import org.mesutormanli.bistadvisor.features.TechnicalFeatures;
import org.mesutormanli.bistadvisor.features.TechnicalFeatures.Bar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Makine öğrenimi modellerini eğiten ve bellek içi önbellekte tutan servis.
 * <p>
 * Her (model türü, endeks adı) ikilisi için ayrı bir model örneği saklanır.
 * Eğitim verisi, endeksteki tüm hisselerin fiyat serileri ve temel verileri
 * kullanılarak oluşturulur.
 */
@Service
public class ModelTrainer {
    private static final Logger log = LoggerFactory.getLogger(ModelTrainer.class);

    private final AppConfig appConfig;
    private final BistIndices bistIndices;
    private final YahooClient yahoo;
    private final CacheStore cacheStore;
    private final Map<String, ModelStrategy> cache = new HashMap<>();

    /**
     * {@code ModelTrainer} servisini kurar. Bağımlılıklar Spring tarafından enjekte edilir.
     */
    public ModelTrainer(AppConfig appConfig, BistIndices bistIndices,
                        YahooClient yahoo, CacheStore cacheStore) {
        this.appConfig = appConfig;
        this.bistIndices = bistIndices;
        this.yahoo = yahoo;
        this.cacheStore = cacheStore;
    }

    /**
     * Model önbellek anahtarını oluşturur: {@code "MODEL_ADI:ENDERS_ADI"}.
     *
     * @param type      model türü
     * @param indexName endeks adı
     * @return önbellek anahtarı
     */
    private static String cacheKey(ModelType type, String indexName) {
        return type.name() + ":" + (indexName != null ? indexName.toUpperCase() : "");
    }

    /**
     * İstenen model türü ve endeks için önbellekteki modeli döndürür;
     * yoksa eğitip önbelleğe alır.
     *
     * @param type      model türü
     * @param indexName endeks adı
     * @return eğitilmiş {@link ModelStrategy} örneği
     */
    public synchronized ModelStrategy getOrTrain(ModelType type, String indexName) {
        String key = cacheKey(type, indexName);
        ModelStrategy s = cache.get(key);
        if (s != null) return s;
        TrainingSet ts = buildTrainingSet(indexName);
        ModelStrategy strategy = ModelStrategyFactory.create(type);
        strategy.train(ts.features, ts.labels);
        cache.put(key, strategy);
        log.info("Model egitildi (bellekte): {}:{} (ornek={})", type, indexName, ts.labels.length);
        return strategy;
    }

    /**
     * Modeli zorla yeniden eğitir (önbelleğe bakmadan) ve saklar.
     *
     * @param type      model türü
     * @param indexName endeks adı
     * @return yeni eğitilmiş {@link ModelStrategy} örneği
     */
    public synchronized ModelStrategy train(ModelType type, String indexName) {
        TrainingSet ts = buildTrainingSet(indexName);
        ModelStrategy s = ModelStrategyFactory.create(type);
        s.train(ts.features, ts.labels);
        cache.put(cacheKey(type, indexName), s);
        log.info("Model egitildi (bellekte): {}:{} (ornek={})", type, indexName, ts.labels.length);
        return s;
    }

    /**
     * Belirtilen endeksteki tüm hisseler için eğitim verisi oluşturur.
     * Her hisse için pencere kaydırarak öznitelik vektörleri ve etiketler üretir.
     *
     * @param indexName endeks adı
     * @return eğitim kümesi ({@link TrainingSet})
     * @throws IllegalStateException hiçbir hisse için veri üretilemezse
     */
    private TrainingSet buildTrainingSet(String indexName) {
        List<double[]> rows = new ArrayList<>();
        List<Integer> labels = new ArrayList<>();
        int horizon = appConfig.labelHorizonDays();
        List<String> symbols = bistIndices.symbolsOf(indexName);
        for (String sym : symbols) {
            List<Bar> bars = loadSeries(sym);
            if (bars.size() <= horizon + 5) continue;
            Fundamentals f = yahoo.fetchFundamentals(sym);
            int end = bars.size() - horizon;
            int windowSize = Math.min(bars.size(), 100);
            for (int i = Math.max(0, end - windowSize); i < end; i++) {
                List<Bar> window = bars.subList(0, i + 1);
                FeatureVector fv = FeatureVector.fromBars(f, window);
                rows.add(fv.normalize().toArray());
                labels.add(Labeler.labelFor(bars, horizon, i));
            }
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException("canli egitim verisi uretilemedi (endeks: " + indexName + ")");
        }
        double[][] x = rows.toArray(new double[0][]);
        int[] y = labels.stream().mapToInt(Integer::intValue).toArray();
        logClassDistribution(y);
        return new TrainingSet(x, y);
    }

    /**
     * Eğitim setindeki sınıf dağılımını loglar. Bir sınıf hiç oluşmamışsa uyarı basar;
     * modeller bu sınıfı üretemez (ör. yatay piyasada SAT etiketi yoksa).
     *
     * @param y etiket dizisi
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

    private record TrainingSet(double[][] features, int[] labels) {}

    /**
     * Belirtilen hisse için fiyat serisini önbellekten veya Yahoo Finance'den yükler.
     *
     * @param symbol hisse sembolü
     * @return fiyat çubukları listesi
     */
    private List<Bar> loadSeries(String symbol) {
        if (!cacheStore.hasFresh(symbol)) {
            List<Bar> fetched = yahoo.fetchPrices(symbol);
            if (!fetched.isEmpty()) {
                cacheStore.writeLines(symbol, fetched.stream()
                        .map(b -> b.date() + "," + b.close() + "," + (long) b.volume()).toList());
            }
        }
        return TechnicalFeatures.toBars(cacheStore.readLines(symbol));
    }
}
