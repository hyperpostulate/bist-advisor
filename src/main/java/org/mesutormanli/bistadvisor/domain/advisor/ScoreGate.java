package org.mesutormanli.bistadvisor.domain.advisor;

/**
 * Model türüne göre yorumlanmış AL/SAT karar kapıları (skor eşikleri).
 * <p>
 * Var olma nedeni, oylama tabanlı modellerin (örn. KNN) skorlarının sürekli değil
 * süreksiz olmasıdır: güven, komşu oy oranıdır ve {@code 1/K} adımlı değerler alır.
 * Ham eşikleri bu ızgaraya bakmadan kullanmak, tek bir komşu oyunun tek başına
 * karar tetiklemesine yol açabilir. Bu sınıf şu garantileri verir:
 * <ul>
 *   <li>{@link #effectiveThreshold(double)}: çözünürlüklü (discrete) modellerde
 *     tabanı bir üst oy ızgarasına yuvarlar ve en az 2 × çözünürlük yapar;
 *     yani kararın en az iki oyluk destek gerektirmesini sağlar.</li>
 *   <li>{@link #passes(double, double)}: skoru etkin eşikle, yüzer nokta
 *     toleransıyla karşılaştırır.</li>
 * </ul>
 * Sürekli skorlu modellerde ({@code resolution = 0}) taban eşik olduğu gibi
 * korunur. Modelin skor çözünürlüğü {@code ModelStrategy#scoreResolution()}
 * tarafından bildirilir; bu sınıf model iç detayını bilmez.
 */
public final class ScoreGate {

    private static final double EPS = 1e-9;

    private final double resolution;

    private ScoreGate(double resolution) {
        this.resolution = resolution;
    }

    /**
     * Sürekli skorlu modeller için kapı döndürür.
     *
     * @return eşikleri değiştirmeyen kapı
     */
    public static ScoreGate continuous() {
        return new ScoreGate(0.0);
    }

    /**
     * Oy ızgaralı (süreksiz) modeller için kapı döndürür.
     *
     * @param resolution skor çözünürlüğü (örn. KNN'de {@code 1/K = 0.2})
     * @return eşikleri ızgaraya yuvarlayan kapı
     */
    public static ScoreGate discretized(double resolution) {
        return new ScoreGate(resolution);
    }

    /**
     * Model çözünürlüğüne göre düzeltilmiş (etkin) eşik değerini hesaplar.
     *
     * @param base taban eşik (alım eşiği ya da satış skor eşiği)
     * @return uygulanacak etkin eşik
     * @implNote Izgaralı modellerde taban {@code ceil(base / grid − eps) × grid}
     *           ile yukarı yuvarlanır ve en az {@code 2 × grid} değerine yükseltilir.
     */
    public double effectiveThreshold(double base) {
        if (resolution <= 0) return base;
        double snapped = Math.ceil(base / resolution - EPS) * resolution;
        return Math.max(snapped, 2 * resolution);
    }

    /**
     * Skorun etkin eşiği geçip geçmediğini belirler.
     *
     * @param score sınanan model skoru/güveni
     * @param base  taban eşik
     * @return skor etkin eşiği geçiyorsa {@code true}
     */
    public boolean passes(double score, double base) {
        return score + EPS >= effectiveThreshold(base);
    }
}