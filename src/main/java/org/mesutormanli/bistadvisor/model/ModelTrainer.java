package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.config.AnalysisType;
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
 * Spring servisi: sınıflandırma modellerini canlı veriden eğitir ve eğitilmiş modelleri
 * bellekte {@code Map<String, ModelStrategy>} içinde TİP:ANALİZ:ENDEKS anahtarıyla saklar.
 *
 * <p>Modeller diske kaydedilmez; yalnızca çalışma süresince bellekte tutulur (log mesajı:
 * "bellekte"). Eğitim verisi, endeksteki her sembol için yüklenen fiyat serilerinden
 * geçmişe genişleyen pencerelerle ve ileriye bakışlı etiketlemeyle
 * ({@link Labeler#labelFor}) üretilir. Öznitelikler seçilen {@link AnalysisType}'ın metrik
 * kümesiyle kesilir: {@code YALNIZCA_TEKNIK} modunda temel gösterge verisi ne eğitimde ne
 * tahminde kullanılmaz (ve Yahoo temel veri isteği hiç yapılmaz); bu mod, güncel tarihli
 * temel göstergelerin geçmişe uygulanmasından doğan geleceğe sızıntıyı (look-ahead bias)
 * tamamen kaldırır. {@link #getOrTrain(ModelType, AnalysisType, String)} önbellekten
 * döndürür ya da kurar/eğitir/önbelleğe alar; {@link #train(ModelType, AnalysisType, String)}
 * her zaman yeniden eğitir. Her iki metot da senkrondur.
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
     * Kurar: servisin bağımlılıklarını (yapılandırma, endeks bilgisi, veri istemcisi ve
     * fiyat serisi önbelleği) enjekte eder.
     *
     * @param appConfig   uygulama ayarları (ör. etiket ufku
     *                    {@code bist.model.label-horizon-days})
     * @param bistIndices endeks-sembol eşlemelerini sağlayan kaynak
     * @param yahoo       Yahoo Client ile fiyat/temel veri erişimi
     * @param cacheStore  yerel fiyat serisi CSV önbelleği
     */
    public ModelTrainer(AppConfig appConfig, BistIndices bistIndices,
                        YahooClient yahoo, CacheStore cacheStore) {
        this.appConfig = appConfig;
        this.bistIndices = bistIndices;
        this.yahoo = yahoo;
        this.cacheStore = cacheStore;
    }

    /**
     * Oluşturur: model önbelleği için TİP:ANALİZ:ENDEKS anahtarını hesaplar.
     *
     * @param type         model türü
     * @param analysisType analiz tipi (metrik kümesi)
     * @param indexName    endeks adı (büyük harfe çevrilir)
     * @return {@code "TİP:ANALİZ:ENDEKS"} biçiminde önbellek anahtarı
     * @implNote Anahtar analiz tipini de taşıdığı için farklı metrik kümeleriyle eğitilmiş
     *           modeller birbirine karışmaz; boyut uyuşmazlığı bu sayede imkânsızdır.
     */
    private static String cacheKey(ModelType type, AnalysisType analysisType, String indexName) {
        return type.name() + ":" + analysisType.name() + ":"
                + (indexName != null ? indexName.toUpperCase() : "");
    }

    /**
     * Döndürür: önbellekteki modeli verir; yoksa kurar, eğitir ve önbelleğe alar.
     *
     * @param type         model türü
     * @param analysisType analiz tipi (metrik kümesi)
     * @param indexName    endeks adı
     * @return eğitilmiş {@link ModelStrategy} örneği (önbellekte varsa o döndürülür)
     * @throws IllegalStateException eğitim verisi hiç üretilemezse
     */
    public synchronized ModelStrategy getOrTrain(ModelType type, AnalysisType analysisType, String indexName) {
        String key = cacheKey(type, analysisType, indexName);
        ModelStrategy s = cache.get(key);
        if (s != null) return s;
        TrainingSet ts = buildTrainingSet(indexName, analysisType);
        ModelStrategy strategy = ModelStrategyFactory.create(type);
        strategy.train(ts.features, ts.labels, analysisType);
        cache.put(key, strategy);
        log.info("Model egitildi (bellekte): {}:{}:{} (ornek={})", type, analysisType, indexName, ts.labels.length);
        return strategy;
    }

    /**
     * Eğitir: modeli her zaman yeniden kurar ve önbelleği günceller.
     *
     * @param type         model türü
     * @param analysisType analiz tipi (metrik kümesi)
     * @param indexName    endeks adı
     * @return yeni eğitilmiş {@link ModelStrategy} örneği
     * @throws IllegalStateException eğitim verisi hiç üretilemezse
     */
    public synchronized ModelStrategy train(ModelType type, AnalysisType analysisType, String indexName) {
        TrainingSet ts = buildTrainingSet(indexName, analysisType);
        ModelStrategy s = ModelStrategyFactory.create(type);
        s.train(ts.features, ts.labels, analysisType);
        cache.put(cacheKey(type, analysisType, indexName), s);
        log.info("Model egitildi (bellekte): {}:{}:{} (ornek={})", type, analysisType, indexName, ts.labels.length);
        return s;
    }

    /**
     * Oluşturur: endeksteki tüm semboller için öznitelik-etiket eğitim setini kurar.
     *
     * <p>Her sembol için fiyat serisi yüklenir (yerel CSV önbelleği taze değilse Yahoo'dan
     * çekilir) ve {@code bars.size() > horizon + 5} şartını sağlamayan seriler atlanır. Kalan
     * serilerde son 100 örneklik pencerede (seri daha kısa ise tamamı) örnek üretilir:
     * örnek indeksleri {@code end - windowSize} ile {@code end - 1} arasındadır
     * ({@code end = bars.size() - horizon}), böylece etiketin geleceği her zaman görür.
     * Her örnek için geçmişe genişleyen pencere ({@code bars.subList(0, i + 1)}) üzerinden
     * öznitelik vektörü üretilir ve etiket {@link Labeler#labelFor} ile verilir.
     * Üretilen vektörler {@link FeatureVector#toArray(AnalysisType)} ile seçilen analiz
     * tipinin metrik kümesine kesilir; temel göstergeler yalnızca analiz tipi
     * {@link AnalysisType#usesFundamental()} ise çekilir (teknik modda temel veriye hiç
     * dokunulmaz). Ufuk {@code labelHorizonDays} (varsayılan 20,
     * {@code bist.model.label-horizon-days}).
     *
     * @param indexName    endeks adı
     * @param analysisType eğitimde kullanılacak metrik kümesi
     * @return eğitim seti ({@link TrainingSet}: özellik matrisi + etiket dizisi)
     * @throws IllegalStateException hiçbir sembol için örnek üretilemezse
     */
    private TrainingSet buildTrainingSet(String indexName, AnalysisType analysisType) {
        List<double[]> rows = new ArrayList<>();
        List<Integer> labels = new ArrayList<>();
        int horizon = appConfig.labelHorizonDays();
        List<String> symbols = bistIndices.symbolsOf(indexName);
        for (String sym : symbols) {
            List<Bar> bars = loadSeries(sym);
            if (bars.size() <= horizon + 5) continue;
            Fundamentals f = analysisType.usesFundamental() ? yahoo.fetchFundamentals(sym) : null;
            int end = bars.size() - horizon;
            int windowSize = Math.min(bars.size(), 100);
            for (int i = Math.max(0, end - windowSize); i < end; i++) {
                List<Bar> window = bars.subList(0, i + 1);
                FeatureVector fv = FeatureVector.fromBars(f, window);
                rows.add(fv.normalize().toArray(analysisType));
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
     * Loglar: eğitim setindeki AL/SAT/TUT sınıf dağılımını raporlar.
     *
     * <p>Bir sınıf hiç oluşmamışsa uyarı basar: modeller eksik sınıfı üretemez, bu yüzden
     * karar eşikleri esnekleştirilmelidir.
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

    /**
     * Sarar: bir eğitim setinin özellik matrisi ve etiket dizisini birlikte taşır.
     *
     * @param features özellik matrisi ({@code double[N][k]}; {@code k} =
     *                 {@code FeatureVector.dimension(analysisType)})
     * @param labels   sınıf etiketleri ({@code int[N]}; 0=AL, 1=SAT, 2=TUT)
     */
    private record TrainingSet(double[][] features, int[] labels) {}

    /**
     * Yükler: sembolün fiyat serisini yerel önbellekten okur, taze değilse Yahoo Client ile
     * çeker ve önbelleğe yazar.
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
