package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.features.TechnicalFeatures;

import java.util.List;

/**
 * Gelecekteki getiriye göre etiket (AL/SAT/TUT) atayan sınıflandırıcı.
 * <p>
 * {@code horizon} gün sonrasındaki fiyata bakarak:
 * <ul>
 *   <li>&gt; %5 ise {@code BUY} (0)</li>
 *   <li>&lt; -%5 ise {@code SELL} (1)</li>
 *   <li>aksi halde {@code HOLD} (2)</li>
 * </ul>
 */
public final class Labeler {

    /** Alım etiketi (0) — beklenen getiri &gt; %5 */
    public static final int BUY = 0;

    /** Satım etiketi (1) — beklenen getiri &lt; -%5 */
    public static final int SELL = 1;

    /** Tutma etiketi (2) — getiri %5'ten düşük */
    public static final int HOLD = 2;

    private Labeler() {}

    /**
     * Belirtilen endeksteki fiyatı {@code horizon} gün sonrasıyla karşılaştırarak
     * bir etiket döndürür.
     *
     * @param bars        fiyat çubukları serisi
     * @param horizon     ileriye bakma dönemi (gün)
     * @param sampleIndex örneklem indeksi
     * @return {@link #BUY}, {@link #SELL} veya {@link #HOLD}
     */
    public static int labelFor(List<TechnicalFeatures.Bar> bars, int horizon, int sampleIndex) {
        if (sampleIndex + horizon >= bars.size()) return HOLD;
        double now = bars.get(sampleIndex).close();
        double future = bars.get(sampleIndex + horizon).close();
        double ret = (future - now) / now;
        if (ret > 0.05) return BUY;
        if (ret < -0.05) return SELL;
        return HOLD;
    }
}
