package org.mesutormanli.bistadvisor.config;

/** Yatirim risk modu: TEMKINLI (%25 risk), DENGELI (%50), AGRESIF (%75). */
public enum AdvisorMode {
    CONSERVATIVE("TEMKINLI", 0.25, 0.75, 0.10, 0.30),
    BALANCED("DENGELI", 0.50, 0.60, 0.15, 0.25),
    AGGRESSIVE("AGRESIF", 0.75, 0.50, 0.25, 0.20);

    /** Kullaniciya gosterilen Turkce etiket. */
    public final String label;
    /** Alim icin ayrilan nakit yuzdesi (0..1). */
    public final double riskPct;
    /** AL onerisi icin gereken minimum ML guven skoru. */
    public final double buyThreshold;
    /** Bu zarar oraninin uzerinde otomatik SAT sinyali. */
    public final double stopLossPct;
    /** Bu ML skorunun altindaki pozisyonlar SAT olarak isaretlenir. */
    public final double sellScoreThreshold;

    AdvisorMode(String label, double riskPct, double buyThreshold, double stopLossPct, double sellScoreThreshold) {
        this.label = label;
        this.riskPct = riskPct;
        this.buyThreshold = buyThreshold;
        this.stopLossPct = stopLossPct;
        this.sellScoreThreshold = sellScoreThreshold;
    }

    /** label veya name ile eslesen modu dondurur, eslesmezse BALANCED. */
    public static AdvisorMode fromLabel(String label) {
        if (label == null) return BALANCED;
        for (AdvisorMode m : values()) {
            if (m.label.equalsIgnoreCase(label) || m.name().equalsIgnoreCase(label)) return m;
        }
        return BALANCED;
    }
}
