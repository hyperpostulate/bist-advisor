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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ML model egitimi ve bellek-ici onbellek. SMILE KNN ve SVM serilestirmeyi
 * desteklemediginden modeller JVM omru boyunca bellekte tutulur.
 * Egitim seti secili endeks evreninden Yahoo verisi ile dinamik olarak olusturulur.
 */
@Service
public class ModelTrainer {
    private static final Logger log = LoggerFactory.getLogger(ModelTrainer.class);

    private final AppConfig appConfig;
    private final BistIndices bistIndices;
    private final YahooClient yahoo;
    private final CacheStore cacheStore;
    private final Map<String, ModelStrategy> cache = new HashMap<>();

    public ModelTrainer(AppConfig appConfig, BistIndices bistIndices,
                        YahooClient yahoo, CacheStore cacheStore) {
        this.appConfig = appConfig;
        this.bistIndices = bistIndices;
        this.yahoo = yahoo;
        this.cacheStore = cacheStore;
    }

    private static String cacheKey(ModelType type, String indexName) {
        return type.name() + ":" + (indexName != null ? indexName.toUpperCase() : "");
    }

    /** Cache'te varsa dondurur, yoksa canli veriyle egitip cache'e ekler. */
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

    /** CLI train komutu icin zorla egitim, sonucu cache'e yazar. */
    public synchronized ModelStrategy train(ModelType type, String indexName) {
        TrainingSet ts = buildTrainingSet(indexName);
        ModelStrategy s = ModelStrategyFactory.create(type);
        s.train(ts.features, ts.labels);
        cache.put(cacheKey(type, indexName), s);
        log.info("Model egitildi (bellekte): {}:{} (ornek={})", type, indexName, ts.labels.length);
        return s;
    }

    /** Secili endeks evreninden teknik + temel ozellik matrisi ve getiri tabanli etiketler olusturur. */
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
            for (int i = Math.max(0, end - 20); i < end; i++) {
                List<Bar> window = bars.subList(0, i + 1);
                FeatureVector fv = FeatureVector.fromBars(f, window);
                fv.normalize();
                rows.add(fv.toArray());
                labels.add(Labeler.labelFor(bars, horizon, i));
            }
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException("canli egitim verisi uretilemedi (endeks: " + indexName + ")");
        }
        double[][] x = rows.toArray(new double[0][]);
        int[] y = labels.stream().mapToInt(Integer::intValue).toArray();
        return new TrainingSet(x, y);
    }

    private record TrainingSet(double[][] features, int[] labels) {}

    /** Bir sembolun fiyat serisini cache'ten veya Yahoo'dan yukler. */
    private List<Bar> loadSeries(String symbol) {
        LocalDate today = LocalDate.now();
        if (!cacheStore.hasFresh(symbol, today)) {
            List<Bar> fetched = yahoo.fetchPrices(symbol);
            if (!fetched.isEmpty()) {
                cacheStore.writeLines(symbol, fetched.stream()
                        .map(b -> b.date() + "," + b.close() + "," + (long) b.volume()).toList());
            }
        }
        return TechnicalFeatures.toBars(cacheStore.readLines(symbol));
    }
}
