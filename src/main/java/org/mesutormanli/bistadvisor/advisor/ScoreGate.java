package org.mesutormanli.bistadvisor.advisor;

import org.mesutormanli.bistadvisor.config.ModelType;
import org.mesutormanli.bistadvisor.model.KnnStrategy;

/**
 * Model türüne göre yorumlanmış skor eşikleri (AL/SAT karar kapıları).
 * <p>
 * Üç modelin skorları farklı ölçeklerde yaşar: KNN komşu oy oranı (k=5 için 1/5
 * paylarla konuşur), RandomForest one-vs-rest olasılık, SVM ise sigmoid(marj).
 * Ham eşikleri üç modelde de olduğu gibi karşılaştırmak, örn. KNN'de 1 komşunun
 * (pay=0.2) satış için yeterli sayılması gibi tehlikeli gevşekliğe yol açar.
 * Bu sınıf:
 * <ul>
 *   <li>KNN eşiklerini ulaşılabilir oy paylarına tavanlı yuvarlar ve en az
 *       {@code 2/k} komşu anlaşması şartı koşar;</li>
 *   <li>RandomForest/SVM için temel eşiği olduğu gibi kullanır — skor
 *       kalibrasyonu P1 yol haritasındadır (Platt/isotonic).</li>
 * </ul>
 */
public final class ScoreGate {

    private static final double EPS = 1e-9;

    private ScoreGate() {}

    /**
     * Model türüne göre düzeltilmiş eşiği döndürür.
     *
     * @param type model türü
     * @param base temel eşik ({@code AdvisorMode.buyThreshold} /
     *             {@code AdvisorMode.sellScoreThreshold})
     * @return uygulanacak eşik
     */
    public static double effectiveThreshold(ModelType type, double base) {
        return switch (type) {
            case KNN -> {
                double grid = 1.0 / KnnStrategy.DEFAULT_K;
                double snapped = Math.ceil(base / grid - EPS) * grid;
                // en az 2 komşu anlasmasi: tek komşunun oyu tek başına karar üretemez
                yield Math.max(snapped, 2 * grid);
            }
            // kalibrasyon P1'de: simdilik temel esik gecerli
            case SVM, RANDOM_FOREST -> base;
        };
    }

    /**
     * Bir skorun, model türüne göre düzeltilmiş eşiği geçip geçmediğini belirler.
     *
     * @param type  model türü
     * @param score tahmin güven skoru
     * @param base  temel eşik
     * @return {@code true} eğer skor eşiği geçiyorsa
     */
    public static boolean passes(ModelType type, double score, double base) {
        return score + EPS >= effectiveThreshold(type, base);
    }
}