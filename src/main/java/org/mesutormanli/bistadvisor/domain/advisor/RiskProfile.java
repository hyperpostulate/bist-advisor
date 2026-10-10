package org.mesutormanli.bistadvisor.domain.advisor;

/**
 * Yatırım risk profili: risk yüzdesi, alım eşiği, stop-loss yüzdesi ve satış skor
 * eşiğini birlikte belirler.
 * <p>
 * Sabitler sırasıyla görünen ad, portföyden harcanacak risk yüzdesi
 * ({@code riskPct}), alım skor eşiği ({@code buyThreshold}), stop-loss yüzdesi
 * ({@code stopLossPct}) ve satış skor eşiğidir ({@code sellScoreThreshold}).
 * {@link #fromLabel(String)} hem adı hem görünen Türkçe etiketi çözer;
 * tanınmayan girdilerde {@link #BALANCED} varsaylanır.
 */
public enum RiskProfile {
    CONSERVATIVE("TEMKINLI", 0.25, 0.75, 0.10, 0.30),
    BALANCED("DENGELI", 0.50, 0.60, 0.15, 0.25),
    AGGRESSIVE("AGRESIF", 0.75, 0.50, 0.25, 0.20);

    /** Arayüzlerde görünen Türkçe ad. */
    public final String label;

    /** Planlanan sermayeden harcanacak risk yüzdesi. */
    public final double riskPct;

    /** Alım adayının skor eşiği. */
    public final double buyThreshold;

    /** Stop-loss zarar yüzdesi (örn. 0.15 = −%15). */
    public final double stopLossPct;

    /** Modelin SAT sinyali için gereken skor eşiği. */
    public final double sellScoreThreshold;

    RiskProfile(String label, double riskPct, double buyThreshold,
                double stopLossPct, double sellScoreThreshold) {
        this.label = label;
        this.riskPct = riskPct;
        this.buyThreshold = buyThreshold;
        this.stopLossPct = stopLossPct;
        this.sellScoreThreshold = sellScoreThreshold;
    }

    /**
     * Ad ya da görünen etiketten risk profilini çözer.
     *
     * @param label çözümlenecek metin ({@code null} olabilir)
     * @return eşleşen profil; tanınmayan girdide {@link #BALANCED}
     */
    public static RiskProfile fromLabel(String label) {
        if (label == null) return BALANCED;
        for (RiskProfile m : values()) {
            if (m.label.equalsIgnoreCase(label) || m.name().equalsIgnoreCase(label)) return m;
        }
        return BALANCED;
    }
}