package org.mesutormanli.bistadvisor.config;

/**
 * Yatırımcı risk profiline göre üç farklı modu tanımlar:
 * <ul>
 *   <li>{@code CONSERVATIVE} (TEMKINLI) — düşük risk, sıkı alım eşiği</li>
 *   <li>{@code BALANCED} (DENGELI) — dengeli risk</li>
 *   <li>{@code AGGRESSIVE} (AGRESIF) — yüksek risk, agresif alım</li>
 * </ul>
 * <p>
 * Her mod kendine özgü risk yüzdesi, alım eşiği, stop-loss ve satış skoru
 * eşik değerlerine sahiptir.
 */
public enum AdvisorMode {
    CONSERVATIVE("TEMKINLI", 0.25, 0.75, 0.10, 0.30),
    BALANCED("DENGELI", 0.50, 0.60, 0.15, 0.25),
    AGGRESSIVE("AGRESIF", 0.75, 0.50, 0.25, 0.20);

    public final String label;

    public final double riskPct;

    public final double buyThreshold;

    public final double stopLossPct;

    public final double sellScoreThreshold;

    AdvisorMode(String label, double riskPct, double buyThreshold, double stopLossPct, double sellScoreThreshold) {
        this.label = label;
        this.riskPct = riskPct;
        this.buyThreshold = buyThreshold;
        this.stopLossPct = stopLossPct;
        this.sellScoreThreshold = sellScoreThreshold;
    }

    /**
     * Metin etiketine ({@code "TEMKINLI"}, {@code "DENGELI"}, {@code "AGRESIF"} ya da
     * enum adı) göre uygun {@code AdvisorMode} değerini döndürür.
     * Eşleşme bulunamazsa varsayılan olarak {@code BALANCED} döner.
     *
     * @param label mod etiketi (case-insensitive)
     * @return eşleşen {@code AdvisorMode} sabiti
     */
    public static AdvisorMode fromLabel(String label) {
        if (label == null) return BALANCED;
        for (AdvisorMode m : values()) {
            if (m.label.equalsIgnoreCase(label) || m.name().equalsIgnoreCase(label)) return m;
        }
        return BALANCED;
    }
}
