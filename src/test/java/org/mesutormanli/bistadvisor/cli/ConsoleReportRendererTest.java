package org.mesutormanli.bistadvisor.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mesutormanli.bistadvisor.application.AnalysisResult;
import org.mesutormanli.bistadvisor.application.Recommendation;
import org.mesutormanli.bistadvisor.domain.advisor.Signal;
import org.mesutormanli.bistadvisor.domain.portfolio.PortfolioSnapshot;
import org.mesutormanli.bistadvisor.domain.portfolio.Position;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ConsoleReportRenderer} konsol metinlerinin sözleşmesi: eski CLI çıktılarıyla
 * birebir aynı satırların üretilmesi ve sıranın korunması.
 * <p>
 * Format maskeleri ({@code %,.0f}, {@code %,.2f}, {@code %.2f}) yerel ayara duyarlı
 * olduğundan beklenen metinler de aynı maskelerle üretilir; test metnin çevresindeki
 * sabit Türkçe metinleri ve satır düzenini kilitler.
 */
class ConsoleReportRendererTest {

    private ByteArrayOutputStream buffer;
    private ConsoleReportRenderer renderer;

    @BeforeEach
    void setUp() {
        buffer = new ByteArrayOutputStream();
        renderer = new ConsoleReportRenderer(new PrintStream(buffer, true, StandardCharsets.UTF_8));
    }

    /**
     * Çıktının satırlarını platform satır ayıracıyla böler.
     *
     * @return konsola yazılan satırlar
     */
    private List<String> lines() {
        return List.of(buffer.toString(StandardCharsets.UTF_8)
                .split(java.util.regex.Pattern.quote(System.lineSeparator()), -1));
    }

    /**
     * "Günlük Öneri" raporunun eski satırlarla birebir üretilmesi.
     * <p>
     * Senaryo: TUT holdingi (+%4,17, fiyatlı), fiyat verisi olmayan SAT holdingi ve
     * tek AL önerisi içeren sonuç 2 pozisyonla yazdırılır. Beklenen davranış: başlık
     * ({@code "=== Günlük Öneri (2/5) | Nakit: 1235 TL ==="}), uyarı satırı
     * ({@code "! ..."}), fiyatlı holdingde TL kâr/zarar ({@code 600 TL}), fiyatsız
     * holdingde kâr/zararsız satır, alım satırında lot/fiyat/skor ve yasal uyarı
     * satırı sırasıyla üretilir.
     */
    @Test
    void dailyRecommendationReportProducedWithLegacyText() {
        AnalysisResult r = new AnalysisResult(
                List.of(new Recommendation(1, "THYAO", Signal.TUT, 60, 250.0, 0.42, "+4.17%"),
                        new Recommendation(2, "ASELS", Signal.SAT, 50, null, null, "veri yok")),
                List.of(new Recommendation(3, "AKBNK", Signal.AL, 500, 10.0, 0.90, "risk payi")),
                1_234.5, 2, 5, 1, List.of("Fiyat verisi alinamadi: ASELS"));

        renderer.renderReport(r,
                List.of(new Position("THYAO", 60, 240), new Position("ASELS", 50, 351)));

        List<String> lines = lines();
        assertEquals("=== Günlük Öneri (2/5) | Nakit: 1235 TL ===", lines.get(0));
        assertEquals("! Fiyat verisi alinamadi: ASELS", lines.get(1));
        assertEquals("-- Mevcut Portföy --", lines.get(2));
        assertEquals("1) THYAO 60 lot | TUT | +4.17% | "
                + String.format("%,.0f", 600.0) + " TL", lines.get(3));
        assertEquals("2) ASELS 50 lot | SAT | veri yok", lines.get(4));
        assertEquals("-- Al Önerileri --", lines.get(5));
        assertEquals("3) AKBNK 500 lot @ " + String.format("%,.2f", 10.0)
                + " | skor=" + String.format("%.2f", 0.90), lines.get(6));
        assertEquals("* Yatırım tavsiyesi değildir.", lines.get(7));
    }

    /**
     * Alım önerisi yokken eski "(Al önerisi yok)" satırının üretilmesi.
     * <p>
     * Senaryo: boş holding ve alım listeleriyle rapor yazdırılır. Beklenen davranış:
     * iki bölüm de boş görünür ve alım bölümünde
     * {@code "(Al önerisi yok)"} satırı bulunur.
     */
    @Test
    void emptyBuyListProducesLegacyWarningLine() {
        renderer.renderReport(new AnalysisResult(List.of(), List.of(), 0, 0, 5, 5, List.of()),
                List.of());

        List<String> lines = lines();
        assertEquals("-- Mevcut Portföy --", lines.get(1));
        assertTrue(lines.contains("(Al önerisi yok)"), buffer.toString(StandardCharsets.UTF_8));
    }

    /**
     * {@code init} özet satırının eski metinle üretilmesi.
     * <p>
     * Senaryo: 50.000 TL sermaye, 8.450 TL nakit ve seçimler yazdırılır. Beklenen
     * davranış: {@code "Portföy kaydedildi: sermaye=50000.0 TL, nakit=8450 TL,
     * mod=BALANCED, model=RANDOM_FOREST, analiz=TECHNICAL"} satırı üretilir.
     */
    @Test
    void initSummaryProducedWithLegacyText() {
        renderer.renderInitSummary(50_000, 8_450, "BALANCED", "RANDOM_FOREST", "TECHNICAL");

        assertEquals("Portföy kaydedildi: sermaye=50000.0 TL, nakit=8450 TL, "
                        + "mod=BALANCED, model=RANDOM_FOREST, analiz=TECHNICAL",
                lines().get(0));
    }

    /**
     * {@code status} satırlarının eski metinlerle üretilmesi.
     * <p>
     * Senaryo: tek pozisyonlu anlık görüntü yazdırılır. Beklenen davranış: sermaye/
     * nakit satırı, mod/model/analiz satırı, {@code "Pozisyonlar (1/5):"} başlığı ve
     * {@code "  THYAO 10 lot @ 240.0"} pozisyon satırı sırasıyla üretilir.
     */
    @Test
    void statusLinesProducedWithLegacyText() {
        PortfolioSnapshot s = new PortfolioSnapshot();
        s.budget = 50_000;
        s.cash = 8_450;
        s.advisorMode = "BALANCED";
        s.modelType = "RANDOM_FOREST";
        s.analysisType = "TECHNICAL_FUNDAMENTAL";
        s.positions = new java.util.ArrayList<>(List.of(new Position("THYAO", 10, 240)));

        renderer.renderStatus(s, 5);

        List<String> lines = lines();
        assertEquals("Sermaye: 50000 TL | Nakit: 8450 TL", lines.get(0));
        assertEquals("Mod: BALANCED | Model: RANDOM_FOREST | Analiz: TECHNICAL_FUNDAMENTAL",
                lines.get(1));
        assertEquals("Pozisyonlar (1/5):", lines.get(2));
        assertEquals("  THYAO 10 lot @ 240.0", lines.get(3));
    }

    /**
     * {@code confirm} özet ve hata satırlarının eski metinlerle üretilmesi.
     * <p>
     * Senaryo: 1 uygulanan işlem ve tek hata yazdırılır. Beklenen davranış: önce
     * {@code "İşlemler uygulandı: 1"}, sonra {@code "Hatalar:"} başlığı ve hata
     * satırı üretilir; hata yokken başlık basılmaz.
     */
    @Test
    void confirmSummaryProducedWithLegacyText() {
        renderer.renderConfirmSummary(1, List.of("Islem basarisiz: THYAO,SAT,x,2"));

        List<String> lines = lines();
        assertEquals("İşlemler uygulandı: 1", lines.get(0));
        assertEquals("Hatalar:", lines.get(1));
        assertEquals("Islem basarisiz: THYAO,SAT,x,2", lines.get(2));

        buffer.reset();
        renderer.renderConfirmSummary(0, List.of());
        assertEquals("İşlemler uygulandı: 0", lines().get(0));
    }

    /**
     * Uyarı ve {@code train} satırlarının eski metinlerle üretilmesi.
     * <p>
     * Senaryo: doğrulama uyarısı, eğitim başlangıç ve bitiş satırları yazdırılır.
     * Beklenen davranış: {@code "Uyari: ..."}, {@code "Model egitimi (...) yapiliyor..."}
     * ve {@code "Model egitildi (bellekte): ..."} metinleri üretilir.
     */
    @Test
    void warningAndTrainingLinesProducedWithLegacyText() {
        renderer.renderWarning("Negatif nakit (-1000 TL)");
        renderer.renderTrainStart("RANDOM_FOREST", "TECHNICAL", "BIST_30");
        renderer.renderTrainDone("random_forest", "technical");

        List<String> lines = lines();
        assertEquals("Uyari: Negatif nakit (-1000 TL)", lines.get(0));
        assertEquals("Model egitimi (RANDOM_FOREST, analiz=TECHNICAL, endeks=BIST_30) yapiliyor...",
                lines.get(1));
        assertEquals("Model egitildi (bellekte): random_forest (technical)", lines.get(2));
    }
}