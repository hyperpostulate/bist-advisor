package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.config.AppConfig;
import org.mesutormanli.bistadvisor.config.ModelType;
import org.mesutormanli.bistadvisor.data.BistIndices;
import org.mesutormanli.bistadvisor.data.CacheStore;
import org.mesutormanli.bistadvisor.data.PriceScraper;
import org.mesutormanli.bistadvisor.data.YahooFundamentalsScraper;
import org.mesutormanli.bistadvisor.features.FeatureVector;
import org.mesutormanli.bistadvisor.features.TechnicalFeatures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Model egitimi ve bellek-ici cache. SMILE bazi modelleri (KNN, SVM) serilestirmeyi
 * desteklemedigi icin modeller JVM omru boyunca bellekte tutulur.
 * Web modunda uygulama ayakta oldugu surece gecerlidir.
 *
 * Egitim seti: BIST-30 evreninden canli Yahoo fiyat (teknik) + temel verilerle
 * uretilir; etiket son N gunluk getiriye gore AL/SAT/TUT olarak atanir.
 */
@Service
public class ModelTrainer {
    private static final Logger log = LoggerFactory.getLogger(ModelTrainer.class);

    private final AppConfig appConfig;
    private final BistIndices bistIndices;
    private final PriceScraper priceScraper;
    private final YahooFundamentalsScraper fundamentalsScraper;
    private final CacheStore cacheStore;
    private final Map<ModelType, ModelStrategy> cache = new EnumMap<>(ModelType.class);

    public ModelTrainer(AppConfig appConfig, BistIndices bistIndices,
                        PriceScraper priceScraper,
                        YahooFundamentalsScraper fundamentalsScraper, CacheStore cacheStore) {
        this.appConfig = appConfig;
        this.bistIndices = bistIndices;
        this.priceScraper = priceScraper;
        this.fundamentalsScraper = fundamentalsScraper;
        this.cacheStore = cacheStore;
    }

    public ModelStrategy getOrTrain(ModelType type, String indexName) {
        ModelStrategy s = cache.get(type);
        if (s != null) return s;
        double[][] x;
        int[] y;
        try {
            TrainingSet ts = buildTrainingSet(indexName);
            x = ts.features;
            y = ts.labels;
        } catch (Exception e) {
            log.warn("Egitim seti uretilemedi, demo veriye dusuluyor: {}", e.getMessage());
            x = sampleFeatures(30);
            y = sampleLabels(30);
        }
        s = train(type, x, y);
        return s;
    }

    public ModelStrategy train(ModelType type, double[][] features, int[] labels) {
        ModelStrategy strategy = ModelStrategyFactory.create(type);
        strategy.train(features, labels);
        cache.put(type, strategy);
        log.info("Model egitildi (bellekte): {} (ornek={})", type, labels.length);
        return strategy;
    }

    /** CLI 'train' komutu icin canli veriyle egitim (secili endeks). */
    public ModelStrategy train(ModelType type, String indexName) {
        TrainingSet ts = buildTrainingSet(indexName);
        return train(type, ts.features, ts.labels);
    }

    /** Secili endeks evreninden teknik + temel ozellik matrisi ve getiri tabanli etiketler uretir. */
    private TrainingSet buildTrainingSet(String indexName) {
        List<double[]> rows = new ArrayList<>();
        List<Integer> labels = new ArrayList<>();
        int horizon = appConfig.labelHorizonDays();
        List<String> symbols = bistIndices.symbolsOf(indexName);
        for (String sym : symbols) {
            List<String> series = loadSeries(sym);
            if (series.size() <= horizon + 5) continue;
            List<TechnicalFeatures.Bar> bars = TechnicalFeatures.toBars(series);
            Map<String, Double> fundamentals = fundamentalsScraper.fetch(sym);
            int end = bars.size() - horizon;
            // Son ~20 gunluk pencere icin ayri ornek (etiket = horizon gun sonrasi getiri)
            for (int i = Math.max(0, end - 20); i < end; i++) {
                List<TechnicalFeatures.Bar> window = bars.subList(0, i + 1);
                FeatureVector fv = FeatureVector.fromBars(fundamentals, window);
                fv.normalize();
                rows.add(fv.toArray());
                labels.add(Labeler.labelFor(bars, horizon, i));
            }
        }
        if (rows.isEmpty()) {
            log.warn("Canli egitim verisi uretilemedi; demo veriye dusuluyor.");
            return new TrainingSet(sampleFeatures(30), sampleLabels(30));
        }
        double[][] x = rows.toArray(new double[0][]);
        int[] y = labels.stream().mapToInt(Integer::intValue).toArray();
        return new TrainingSet(x, y);
    }

    private record TrainingSet(double[][] features, int[] labels) {
    }

    private List<String> loadSeries(String symbol) {
        LocalDate today = LocalDate.now();
        if (!cacheStore.hasFresh(symbol, today)) {
            List<String> fetched = priceScraper.fetch(symbol);
            if (!fetched.isEmpty()) cacheStore.writePrice(symbol, fetched);
        }
        return cacheStore.readPrice(symbol);
    }

    private double[][] sampleFeatures(int n) {
        double[][] x = new double[n][FeatureVector.featureNames().length];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < x[i].length; j++) x[i][j] = Math.random();
        return x;
    }

    private int[] sampleLabels(int n) {
        int[] y = new int[n];
        for (int i = 0; i < n; i++) y[i] = i % 3;
        return y;
    }
}
