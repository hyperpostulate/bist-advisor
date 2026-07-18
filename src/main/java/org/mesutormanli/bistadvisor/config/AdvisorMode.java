package org.mesutormanli.bistadvisor.config;

/**
 * Yatirim öneri modlari. Web arayuzunden veya CLI'dan konfigure edilir.
 */
public enum AdvisorMode {
    CONSERVATIVE("TEMKINLI", 0.25, 0.75, 0.10, 0.30),
    BALANCED("DENGELI", 0.50, 0.60, 0.15, 0.25),
    AGGRESSIVE("AGRESIF", 0.75, 0.50, 0.25, 0.20);

    /** Kullaniciya gosterilen etiket. */
    public final String label;
    /** Portfoyde kullanilmayan bakiyeden alim icin ayrilan yuzde. */
    public final double riskPct;
    /** Al önerisi icin gereken en dusuk ML skoru. */
    public final double buyThreshold;
    /** Bu zarar oraninin uzerinde Sat sinyali (mali kaybi durdur). */
    public final double stopLossPct;
    /** Bu ML skorunun altinda Sat sinyali. */
    public final double sellScoreThreshold;

    AdvisorMode(String label, double riskPct, double buyThreshold, double stopLossPct, double sellScoreThreshold) {
        this.label = label;
        this.riskPct = riskPct;
        this.buyThreshold = buyThreshold;
        this.stopLossPct = stopLossPct;
        this.sellScoreThreshold = sellScoreThreshold;
    }

    public static AdvisorMode fromLabel(String label) {
        if (label == null) return BALANCED;
        for (AdvisorMode m : values()) {
            if (m.label.equalsIgnoreCase(label) || m.name().equalsIgnoreCase(label)) {
                return m;
            }
        }
        return BALANCED;
    }
}
