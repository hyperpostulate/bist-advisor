package org.mesutormanli.bistadvisor.application;

import org.mesutormanli.bistadvisor.domain.market.Bar;
import org.mesutormanli.bistadvisor.domain.market.Fundamentals;
import org.mesutormanli.bistadvisor.domain.ml.FeatureVector;
import org.mesutormanli.bistadvisor.domain.settings.AnalysisType;
import org.mesutormanli.bistadvisor.ports.MarketDataSource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Öznitelik vektörü üretim hattı: bar serisinden (ve gerekirse temel
 * göstergelerden) modelin beslediği vektörü üretir.
 * <p>
 * Sorumlulukları: temel gösterge verisinin ANALİZ TİPİNE GÖRE koşullandırılması
 * ({@link AnalysisType#usesFundamental()} gerekmüyorsa kaynaktan veri hiç
 * çekilmez, böylece teknik modda temel veriye hiç dokunulmaz) ve ham vektörün
 * normalize edilip analiz tipinin metrik kümesine kesilmesi. Saf dönüşüm
 * ({@code vector}) tek başına çağrılabilir; bu sayede eğitim tarafı her
 * pencere için vektör üretirken aynı kodu paylaşır.
 */
@Service
public class FeaturePipeline {

    private final MarketDataSource source;

    /**
     * Kurar: temel gösterge erişimi için piyasa verisi kaynağını enjekte eder.
     *
     * @param source piyasa verisi kaynağı
     */
    public FeaturePipeline(MarketDataSource source) {
        this.source = source;
    }

    /**
     * Analiz tipinin gerektirdiği temel göstergeleri çeker.
     *
     * @param symbol sembol
     * @param type   analiz tipi
     * @return temel göstergeler; tip gerekmüyorsa {@code null}, veri alınamadıysa
     *         {@link Fundamentals#EMPTY}
     */
    public Fundamentals fundamentalsFor(String symbol, AnalysisType type) {
        return type.usesFundamental() ? source.fetchFundamentals(symbol) : null;
    }

    /**
     * Hazır temel göstergeler ve bar serisinden normalize edilmiş, analiz
     * tipinin metrik kümesine kesilmiş öznitelik vektörünü üretir.
     *
     * @param fundamentals temel göstergeler ({@code null} olabilir)
     * @param bars         bar serisi
     * @param type         vektörün metrik kümesi
     * @return normalize edilmiş öznitelik vektörü
     */
    public double[] vector(Fundamentals fundamentals, List<Bar> bars, AnalysisType type) {
        return FeatureVector.fromBars(fundamentals, bars).normalize().toArray(type);
    }

    /**
     * Sembol ve bar serisinden öznitelik vektörünü üretir; temel göstergeler
     * gerekirse {@link #fundamentalsFor(String, AnalysisType)} ile çekilir.
     *
     * @param symbol sembol
     * @param bars   bar serisi
     * @param type   vektörün metrik kümesi
     * @return normalize edilmiş öznitelik vektörü
     */
    public double[] vector(String symbol, List<Bar> bars, AnalysisType type) {
        return vector(fundamentalsFor(symbol, type), bars, type);
    }
}