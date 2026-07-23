package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.features.TechnicalFeatures;

import java.util.List;

/** N gun sonrasi getiriye gore AL/SAT/TUT etiketi uretir. */
public final class Labeler {
    /** AL (getiri > %5). */
    public static final int BUY = 0;
    /** SAT (getiri < -%5). */
    public static final int SELL = 1;
    /** TUT (aralikta). */
    public static final int HOLD = 2;

    private Labeler() {}

    /**
     * @param bars        kronolojik fiyat serisi (en eski -> en yeni)
     * @param horizon     kac gun sonrasi getiri dikkate alinir
     * @param sampleIndex ornegin serideki indeksi
     * @return BUY (0), SELL (1) veya HOLD (2)
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
