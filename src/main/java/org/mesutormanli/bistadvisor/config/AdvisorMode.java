package org.mesutormanli.bistadvisor.config;

/**
 * Yatırımcı risk profiline göre üç farklı danışman modunu ve bu modların
 * alım/satım kararlarında kullanılan parametrelerini tanımlar.
 * <ul>
 *   <li>{@code CONSERVATIVE} (TEMKINLI): %25 risk yüzdesi, 0.75 alım eşiği,
 *       %10 stop-loss (zarar kes), 0.30 satış skor eşiği.</li>
 *   <li>{@code BALANCED} (DENGELI): %50 risk yüzdesi, 0.60 alım eşiği,
 *       %15 stop-loss, 0.25 satış skor eşiği.</li>
 *   <li>{@code AGGRESSIVE} (AGRESIF): %75 risk yüzdesi, 0.50 alım eşiği,
 *       %25 stop-loss, 0.20 satış skor eşiği.</li>
 * </ul>
 *
 * <p>Parametrelerin anlamı şöyledir:</p>
 * <ul>
 *   <li>{@code riskPct}: planlanmış sermayenin alıma ayrılacak oranı.</li>
 *   <li>{@code buyThreshold}: AL kararı için gereken minimum model skoru
 *       (alım eşiği).</li>
 *   <li>{@code stopLossPct}: ortalama maliyete göre zarar eşiği; k/z yüzdesi
 *       bu değere ulaştığında pozisyon SAT'a işaretlenir.</li>
 *   <li>{@code sellScoreThreshold}: modelin SAT sinyalini uygulamak için
 *       gereken minimum model skoru (satış skor eşiği).</li>
 * </ul>
 *
 * <p>Eşik yorumlaması model türüne göre {@link org.mesutormanli.bistadvisor.advisor.ScoreGate}
 * üzerinden yapılır.</p>
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

    /**
     * Mod sabitini kendi risk parametreleriyle oluşturur.
     *
     * @param label               Türkçe görünen ad ({@code TEMKINLI},
     *                            {@code DENGELI}, {@code AGRESIF})
     * @param riskPct             planlanmış sermayenin alıma ayrılacak oranı (örn. {@code 0.25} = %25 risk)
     * @param buyThreshold        AL için gereken minimum model skoru (örn. {@code 0.75} alım eşiği)
     * @param stopLossPct         ortalama maliyete göre zarar eşiği (örn. {@code 0.10} = %10 stop-loss)
     * @param sellScoreThreshold  modelin SAT sinyalini uygulamak için gereken minimum skor
     */
    AdvisorMode(String label, double riskPct, double buyThreshold, double stopLossPct, double sellScoreThreshold) {
        this.label = label;
        this.riskPct = riskPct;
        this.buyThreshold = buyThreshold;
        this.stopLossPct = stopLossPct;
        this.sellScoreThreshold = sellScoreThreshold;
    }

    /**
     * Metin etiketinden uygun {@link AdvisorMode} sabitini bulur.
     *
     * <p>Türkçe etiket ({@code TEMKINLI}, {@code DENGELI}, {@code AGRESIF}) ya da
     * enum adı ({@code CONSERVATIVE}, {@code BALANCED}, {@code AGGRESSIVE}) ile
     * büyük/küçük harfsiz eşleşme denenir.</p>
     *
     * @param label çözümlenecek mod etiketi
     * @return etiketle eşleşen mod; {@code label} {@code null} ise ya da eşleşme
     *         bulunamazsa varsayılan olarak {@link #BALANCED}
     * @implNote Karşılaştırma {@link String#equalsIgnoreCase(String)} ile yapılır;
     *           dolayısıyla eşleşme büyük/küçük harfe duyarsızdır.
     */
    public static AdvisorMode fromLabel(String label) {
        if (label == null) return BALANCED;
        for (AdvisorMode m : values()) {
            if (m.label.equalsIgnoreCase(label) || m.name().equalsIgnoreCase(label)) return m;
        }
        return BALANCED;
    }
}
