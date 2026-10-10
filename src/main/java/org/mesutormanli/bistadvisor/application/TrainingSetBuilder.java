package org.mesutormanli.bistadvisor.application;

import org.mesutormanli.bistadvisor.domain.market.Bar;
import org.mesutormanli.bistadvisor.domain.market.Fundamentals;
import org.mesutormanli.bistadvisor.domain.ml.Labeler;
import org.mesutormanli.bistadvisor.domain.ml.TrainingSet;
import org.mesutormanli.bistadvisor.domain.settings.AnalysisType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Canlı fiyat verisinden geçmişe genişleyen pencerelerle öznitelik-etiket
 * eğitim seti kuran servis.
 * <p>
 * Her sembol için fiyat serisi {@link PriceSeriesService} ile yüklenir ve
 * {@code bars.size() > horizon + 5} şartını sağlamayan seriler atlanır. Kalan
 * serilerde son 100 örneklik pencerede (seri daha kısa ise tamamı) örnek
 * üretilir: örnek indeksleri {@code end − windowSize} ile {@code end − 1}
 * arasındadır ({@code end = bars.size() − horizon}), böylece etiketin geleceği
 * her zaman görür. Her örnek için geçmişe genişleyen pencere
 * ({@code bars.subList(0, i + 1)}) üzerinden öznitelik vektörü üretilir ve etiket
 * {@link Labeler#labelFor} ile verilir. Temel göstergeler sembol başına bir kez
 * çekilir ve o sembolün tüm örneklerinde ortak kullanılır.
 */
@Service
public class TrainingSetBuilder {

    private final PriceSeriesService prices;
    private final FeaturePipeline features;

    /**
     * Kurar: fiyat yükleme ve öznitelik hatlarını enjekte eder.
     *
     * @param prices   fiyat serisi yükleme servisi
     * @param features öznitelik üretim hattı
     */
    public TrainingSetBuilder(PriceSeriesService prices, FeaturePipeline features) {
        this.prices = prices;
        this.features = features;
    }

    /**
     * Verilen semboller için eğitim setini kurar.
     *
     * @param symbols  endeksin bileşen sembolleri
     * @param type     eğitimde kullanılacak metrik kümesi
     * @param horizon  ileriye bakma dönemi (bar/gün sayısı)
     * @return eğitim seti (özellik matrisi + etiket dizisi); örnek üretilemezse
     *         sıfır uzunluklu
     */
    public TrainingSet build(List<String> symbols, AnalysisType type, int horizon) {
        List<double[]> rows = new ArrayList<>();
        List<Integer> labels = new ArrayList<>();
        for (String sym : symbols) {
            List<Bar> bars = prices.load(sym);
            if (bars.size() <= horizon + 5) continue;
            Fundamentals f = features.fundamentalsFor(sym, type);
            int end = bars.size() - horizon;
            int windowSize = Math.min(bars.size(), 100);
            for (int i = Math.max(0, end - windowSize); i < end; i++) {
                List<Bar> window = bars.subList(0, i + 1);
                rows.add(features.vector(f, window, type));
                labels.add(Labeler.labelFor(bars, horizon, i));
            }
        }
        double[][] x = rows.toArray(new double[0][]);
        int[] y = labels.stream().mapToInt(Integer::intValue).toArray();
        return new TrainingSet(x, y);
    }
}