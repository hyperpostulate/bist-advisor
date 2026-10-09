package org.mesutormanli.bistadvisor.advisor;

import org.mesutormanli.bistadvisor.config.ModelType;
import org.mesutormanli.bistadvisor.model.KnnStrategy;

/**
 * Model türüne göre yorumlanmış AL/SAT karar kapıları (skor eşikleri).
 * <p>
 * Var olma nedeni, KNN skorlarının doğasıdır: KNN güveni komşu oy oranıdır ve
 * {@code 1/K = 0.2}'lik oy paylarıyla sürekli değil süreksiz (discrete) değerler
 * alır. Ham eşikleri bu ızgaraya bakmadan kullanmak, örneğin tek bir komşunun
 * oyunun ({@code 0.2}) tek başına SAT ya da AL kararını tetiklemesine yol açabilir.
 * Bu sınıf şunları garanti eder:
 * </p>
 * <ul>
 *   <li>{@code effectiveThreshold}: KNN'de tabanı bir üst 0.2'lik oy ızgarasına
 *       yuvarlar (tavan) ve en az 2 × grid (0.4) yapar; yani kararın en az iki
 *       komşu oyu gerektirmesini sağlar. SVM ve RANDOM_FOREST'ta taban olduğu
 *       gibi döner.</li>
 *   <li>{@code passes}: skoru etkin eşikle, yüzer nokta toleransıyla karşılaştırır.</li>
 * </ul>
 */
public final class ScoreGate {

    private static final double EPS = 1e-9;

    /**
     * Örnek oluşturulmasını engelleyen gizli yapıcı; sınıf yalnızca statik
     * yardımcı yöntemleriyle kullanılır.
     */
    private ScoreGate() {}

    /**
     * Model türüne göre düzeltilmiş (etkin) eşik değerini hesaplar.
     *
     * @param type eşik yorumlanırken kullanılacak model türü
     * @param base taban eşik ({@code AdvisorMode.buyThreshold} alım eşiği ya da
     *             {@code AdvisorMode.sellScoreThreshold} satış skor eşiği)
     * @return uygulanacak etkin eşik
     * @implNote KNN'de taban, {@code 1/K} (= {@code 0.2}) oy ızgarasına
     *           {@code ceil(base / grid − eps) × grid} ile yukarı yuvarlanır ve en
     *           az {@code 2 × grid} ({@code 0.4}) değerine yükseltilir; böylece tek
     *           komşu oyunun kararı tek başına tetiklemesi engellenir. SVM ve
     *           RANDOM_FOREST için taban aynen döner.
     */
    public static double effectiveThreshold(ModelType type, double base) {
        return switch (type) {
            case KNN -> {
                double grid = 1.0 / KnnStrategy.DEFAULT_K;
                double snapped = Math.ceil(base / grid - EPS) * grid;
                yield Math.max(snapped, 2 * grid);
            }
            case SVM, RANDOM_FOREST -> base;
        };
    }

    /**
     * Skorun, model türüne göre düzeltilmiş eşiği geçip geçmediğini belirler.
     *
     * @param type  eşik yorumlanırken kullanılacak model türü
     * @param score sınanan model skoru/güveni
     * @param base  taban eşik (alım eşiği ya da satış skor eşiği)
     * @return skor etkin eşiği geçiyorsa {@code true}, aksi hâlde {@code false}
     * @implNote Karşılaştırma {@code score + EPS ≥ etkin eşik} biçiminde yapılır;
     *           {@code EPS} ({@code 1e-9}) yüzer nokta yuvarlamasına karşı tolerans
     *           sağlar.
     */
    public static boolean passes(ModelType type, double score, double base) {
        return score + EPS >= effectiveThreshold(type, base);
    }
}