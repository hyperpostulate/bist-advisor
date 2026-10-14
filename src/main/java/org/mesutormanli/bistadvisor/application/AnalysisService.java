package org.mesutormanli.bistadvisor.application;

import org.mesutormanli.bistadvisor.adapters.indices.BistIndices;
import org.mesutormanli.bistadvisor.domain.advisor.AllocationPlanner;
import org.mesutormanli.bistadvisor.domain.advisor.AllocationPlanner.CandidateInput;
import org.mesutormanli.bistadvisor.domain.advisor.AllocationPlanner.HoldingInput;
import org.mesutormanli.bistadvisor.domain.advisor.AllocationPlanner.Plan;
import org.mesutormanli.bistadvisor.domain.advisor.AllocationPlanner.Planned;
import org.mesutormanli.bistadvisor.domain.advisor.RiskProfile;
import org.mesutormanli.bistadvisor.domain.advisor.ScoreGate;
import org.mesutormanli.bistadvisor.domain.market.Bar;
import org.mesutormanli.bistadvisor.domain.ml.ModelStrategy;
import org.mesutormanli.bistadvisor.domain.ml.Prediction;
import org.mesutormanli.bistadvisor.domain.portfolio.PortfolioSnapshot;
import org.mesutormanli.bistadvisor.domain.portfolio.Position;
import org.mesutormanli.bistadvisor.domain.settings.AnalysisType;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Günlük analiz turunu uçtan uca orkestre eden uygulama servisi.
 * <p>
 * Veri hattı ({@link PriceSeriesService}, {@link FeaturePipeline}), model
 * hazırlığı ({@link ModelTrainingService}) ve portföy durumu bir araya getirilir;
 * karar kuralları saf {@link AllocationPlanner}'da kalır. Pozisyonlar
 * {@link HoldingInput}, portföyde olmayan endeks hisseleri ve mevcut pozisyonlar
 * ({@code existing=true} ile eklemeye aday) {@link CandidateInput} olarak
 * planlayıcıya verilir. Planlayıcıya giden skor kapısı modelin skor çözünürlüğüne
 * göre kurulur ({@link ScoreGate}); böylece KNN gibi oy ızgaralı modellerde tek
 * komşu oyu karar tetiklemez. Çıkan plan numaralı önerilere çevrilir ve son
 * çalıştırma tarihi portföy durumuna yazılır ({@code lastRunDate} = bugün).
 */
@Service
public class AnalysisService {

    private final BistIndices bistIndices;
    private final PriceSeriesService prices;
    private final FeaturePipeline features;
    private final ModelTrainingService models;
    private final PortfolioService portfolioService;

    /**
     * Kurar: veri hattı, model servisi ve portföy servisini enjekte eder.
     *
     * @param bistIndices      endeks-sembol eşlemelerini sağlayan katalog
     * @param prices           fiyat serisi yükleme servisi
     * @param features         öznitelik üretim hattı
     * @param models           model yaşam döngüsü servisi
     * @param portfolioService portföy uygulama servisi
     */
    public AnalysisService(BistIndices bistIndices, PriceSeriesService prices,
                           FeaturePipeline features, ModelTrainingService models,
                           PortfolioService portfolioService) {
        this.bistIndices = bistIndices;
        this.prices = prices;
        this.features = features;
        this.models = models;
        this.portfolioService = portfolioService;
    }

    /**
     * Günlük analizi uçtan uca çalıştırır ve öneri listelerini üretir.
     *
     * @return pozisyon önerileri, alım önerileri, nakit, pozisyon/slot sayıları ve
     *         uyarıları içeren analiz sonucu
     * @implNote Bir turda aynı sembol için hem bar serisi hem öznitelik vektörü
     *           çalışma içi memoizasyonla bir kez hesaplanır.
     */
    public AnalysisResult analyze() {
        PortfolioSnapshot state = portfolioService.snapshot();
        RiskProfile mode = portfolioService.riskProfile();
        AnalysisType analysisType = portfolioService.analysisType();
        ModelStrategy model = models.getOrTrain(portfolioService.modelType(), analysisType, state.selectedIndex);
        ScoreGate gate = ScoreGate.discretized(model.scoreResolution());

        Map<String, List<Bar>> barsMemo = new HashMap<>();
        Map<String, double[]> featureMemo = new HashMap<>();

        List<HoldingInput> holdingInputs = new ArrayList<>();
        for (Position p : state.positions) {
            List<Bar> bars = loadSeriesCached(p.symbol(), barsMemo);
            Double price = currentPrice(bars);
            Double score = null;
            Integer predClass = null;
            if (price != null) {
                Prediction pred = model.predict(featuresCached(p.symbol(), bars, analysisType, featureMemo));
                predClass = pred.classLabel();
                score = pred.score();
            }
            holdingInputs.add(new HoldingInput(p.symbol(), p.lots(), p.avgCost(),
                    price, predClass, score));
        }

        Set<String> held = new HashSet<>();
        for (Position p : state.positions) held.add(p.symbol());

        List<CandidateInput> candidateInputs = new ArrayList<>();
        for (String sym : bistIndices.symbolsOf(state.selectedIndex)) {
            if (held.contains(sym)) continue;
            List<Bar> bars = loadSeriesCached(sym, barsMemo);
            Double price = currentPrice(bars);
            if (price == null) continue;
            Prediction pred = model.predict(featuresCached(sym, bars, analysisType, featureMemo));
            candidateInputs.add(new CandidateInput(sym, price, pred.score(), pred.classLabel(), false));
        }
        for (Position p : state.positions) {
            List<Bar> bars = loadSeriesCached(p.symbol(), barsMemo);
            Double price = currentPrice(bars);
            if (price == null) continue;
            Prediction pred = model.predict(featuresCached(p.symbol(), bars, analysisType, featureMemo));
            candidateInputs.add(new CandidateInput(p.symbol(), price, pred.score(), pred.classLabel(), true));
        }

        Plan plan = AllocationPlanner.plan(holdingInputs, candidateInputs,
                mode, gate, portfolioService.availableCash(), portfolioService.maxPositions());

        int idx = 1;
        List<Recommendation> holdings = new ArrayList<>();
        for (Planned h : plan.holdings()) {
            holdings.add(new Recommendation(idx++, h.symbol(), h.action(), h.lots(),
                    h.price(), h.score(), h.note()));
        }
        List<Recommendation> buys = new ArrayList<>();
        for (Planned b : plan.buys()) {
            buys.add(new Recommendation(idx++, b.symbol(), b.action(), b.lots(),
                    b.price(), b.score(), b.note()));
        }

        List<String> warnings = new ArrayList<>();
        if (analysisType.usesFundamental()) {
            warnings.add("Analiz tipi " + analysisType.label
                    + ": temel göstergeler güncel tarihli olduğu için geçmişe dönük çalışmalarda yanıltıcı olabilir");
        }
        warnings.addAll(plan.warnings());

        portfolioService.markRun(LocalDate.now());
        return new AnalysisResult(List.copyOf(holdings), List.copyOf(buys),
                portfolioService.availableCash(), state.positions.size(),
                portfolioService.maxPositions(), plan.buySlots(), List.copyOf(warnings));
    }

    /**
     * Mevcut pozisyonların güncel fiyatlarını toplar.
     *
     * @return sembol → son kapanış fiyatı eşlemesi; serisi/verisi olmayan semboller
     *         atlanır ve pozisyonların portföydeki sırası korunur
     */
    public Map<String, Double> currentPrices() {
        Map<String, Double> pricesOut = new LinkedHashMap<>();
        for (Position p : portfolioService.positions()) {
            Double price = currentPrice(prices.load(p.symbol()));
            if (price != null) pricesOut.put(p.symbol(), price);
        }
        return pricesOut;
    }

    /**
     * Bar serisinin son kapanış fiyatını (güncel fiyat) döndürür.
     * <p>
     * Son kapanış sonlu ve pozitif değilse (veri hatasıyla {@code 0.0} veya
     * {@code NaN} gelirse) fiyat bilinmiyor sayılır ve {@code null} döner; böylece
     * sıfır fiyatlı bir aday alım planına girip tamsayı taşmasına yol açamaz.
     *
     * @param series kapanış fiyatları içeren bar serisi
     * @return serinin son kapanışı; seri boşsa veya fiyat geçersizse {@code null}
     */
    private Double currentPrice(List<Bar> series) {
        if (series.isEmpty()) return null;
        double close = series.getLast().close();
        return (Double.isFinite(close) && close > 0) ? close : null;
    }

    /**
     * Sembolün bar serisini çalışma içi önbellekten döndürür, yoksa yükler.
     *
     * @param symbol yüklenecek hissenin sembolü
     * @param memo   çalışma süresince geçerli sembol → bar listesi haritası
     * @return sembolün bar serisi
     */
    private List<Bar> loadSeriesCached(String symbol, Map<String, List<Bar>> memo) {
        return memo.computeIfAbsent(symbol, prices::load);
    }

    /**
     * Sembolün öznitelik vektörünü çalışma içi önbellekten döndürür, yoksa hesaplar.
     *
     * @param symbol       öznitelikleri hesaplanacak hissenin sembolü
     * @param bars         sembolün bar serisi
     * @param analysisType vektörün metrik kümesi
     * @param memo         çalışma süresince geçerli sembol → vektör haritası
     * @return normalize edilmiş, analiz tipinin kümesine kesilmiş öznitelik vektörü
     */
    private double[] featuresCached(String symbol, List<Bar> bars, AnalysisType analysisType,
                                    Map<String, double[]> memo) {
        return memo.computeIfAbsent(symbol,
                s -> features.vector(s, bars, analysisType));
    }
}