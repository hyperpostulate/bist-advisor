package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.features.TechnicalFeatures;

import java.util.List;

/**
 * Geçmiş fiyat serisinden öğrenme etiketi üretir.
 * N gün sonrası getiriye göre: >%5 AL, <-%5 SAT, arada TUT.
 */
public final class Labeler {
    public static final int BUY = 0;
    public static final int SELL = 1;
    public static final int HOLD = 2;

    private Labeler() {
    }

    /**
     * @param bars        kronolojik fiyat serisi
     * @param horizon     kaç gün sonrası getiri dikkate alınır
     * @param sampleIndex etiketi hesaplanacak örnek (geçmişte bir gün)
     * @return BUY/SELL/HOLD
     */
    public static int labelFor(List<TechnicalFeatures.Bar> bars, int horizon, int sampleIndex) {
        if (sampleIndex + horizon >= bars.size()) return HOLD;
        double now = bars.get(sampleIndex).close();
        double future = bars.get(sampleIndex + horizon).close();
        double ret = (future - now) / now;
        // Eşikler: güçlü yükseliş AL, güçlü düşüş SAT
        if (ret > 0.05) return BUY;
        if (ret < -0.05) return SELL;
        return HOLD;
    }
}
