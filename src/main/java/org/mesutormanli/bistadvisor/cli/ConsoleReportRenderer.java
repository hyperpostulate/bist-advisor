package org.mesutormanli.bistadvisor.cli;

import org.mesutormanli.bistadvisor.application.AnalysisResult;
import org.mesutormanli.bistadvisor.application.Recommendation;
import org.mesutormanli.bistadvisor.domain.portfolio.PortfolioSnapshot;
import org.mesutormanli.bistadvisor.domain.portfolio.Position;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CLI komutlarının konsol çıktı biçiminin tek sahibi: tüm Türkçe özet/döküm
 * metinleri bu sınıf üzerinden yazılır.
 * <p>
 * Çıktı akışı kurucuda verilir (varsayılan {@code System.out}); böylece testlerde
 * bellek içi akışa yazdırılabilir. Metinler eski sürümlerle birebir aynıdır.
 */
public class ConsoleReportRenderer {

    private final PrintStream out;

    /**
     * Kurar: çıktı akışını belirler.
     *
     * @param out yazılacak çıktı akışı
     */
    public ConsoleReportRenderer(PrintStream out) {
        this.out = out;
    }

    /**
     * Kurar: çıktıyı standart çıktı akışına yazar.
     */
    public ConsoleReportRenderer() {
        this(System.out);
    }

    /**
     * "Günlük Öneri" raporunu konsola yazar.
     * <p>
     * Başlıkta pozisyon sayısı (en fazla 5) ve nakit verilir; uyarılar {@code !} önekiyle
     * basılır. "Mevcut Portföy" bölümünde fiyatı çekilemeyen semboller skorsuz satır olarak
     * geçer; fiyatı varsa TL kâr/zarar {@code (fiyat − ortalama maliyet) × lot} ile yazılır.
     * "Al Önerileri" bölümünde lot, fiyat ve skor verilir; rapor "Yatırım tavsiyesi değildir."
     * yasal uyarı satırıyla kapanır.
     *
     * @param r         konsola yazdırılacak günlük analiz sonucu
     * @param positions mevcut pozisyonlar (kâr/zarar hesabı için ortalama maliyet kaynağı)
     */
    public void renderReport(AnalysisResult r, List<Position> positions) {
        out.println("=== Günlük Öneri (" + r.positionCount() + "/5) | Nakit: " + Math.round(r.availableCash()) + " TL ===");
        if (!r.warnings().isEmpty()) {
            r.warnings().forEach(w -> out.println("! " + w));
        }
        out.println("-- Mevcut Portföy --");
        Map<String, Position> posMap = new HashMap<>();
        for (Position p : positions) posMap.put(p.symbol(), p);
        for (Recommendation x : r.holdings()) {
            Position p = posMap.get(x.symbol());
            if (x.price() == null) {
                out.println(x.index() + ") " + x.symbol() + " " + x.lots() + " lot | "
                        + x.action() + " | " + x.note());
                continue;
            }
            double pnlTl = p != null ? (x.price() - p.avgCost()) * x.lots() : 0;
            out.println(x.index() + ") " + x.symbol() + " " + x.lots() + " lot | " + x.action()
                    + " | " + x.note() + " | " + String.format("%,.0f", pnlTl) + " TL");
        }
        out.println("-- Al Önerileri --");
        if (r.buys().isEmpty()) out.println("(Al önerisi yok)");
        for (Recommendation x : r.buys()) {
            out.println(x.index() + ") " + x.symbol() + " " + x.lots() + " lot @ "
                    + String.format("%,.2f", x.price()) + " | skor=" + String.format("%.2f", x.score()));
        }
        out.println("* Yatırım tavsiyesi değildir.");
    }

    /**
     * Doğrulama uyarısını {@code "Uyari: "} önekiyle yazar.
     *
     * @param message uyarı metni
     */
    public void renderWarning(String message) {
        out.println("Uyari: " + message);
    }

    /**
     * {@code init} özet satırını yazar.
     *
     * @param budget      sermaye (TL)
     * @param cash        kalan nakit (TL)
     * @param modeName    yatırım modu adı
     * @param modelName   model adı
     * @param analysisName analiz tipi adı
     */
    public void renderInitSummary(double budget, double cash, String modeName,
                                  String modelName, String analysisName) {
        out.println("Portföy kaydedildi: sermaye=" + budget + " TL, nakit="
                + Math.round(cash) + " TL, mod=" + modeName
                + ", model=" + modelName + ", analiz=" + analysisName);
    }

    /**
     * {@code status} özetini yazar: sermaye/nakit, mod/model/analiz ve pozisyon listesi.
     *
     * @param s           portföy anlık görüntüsü
     * @param maxPositions izin verilen maksimum pozisyon sayısı
     */
    public void renderStatus(PortfolioSnapshot s, int maxPositions) {
        out.println("Sermaye: " + Math.round(s.budget) + " TL | Nakit: " + Math.round(s.cash) + " TL");
        out.println("Mod: " + s.advisorMode + " | Model: " + s.modelType
                + " | Analiz: " + s.analysisType);
        out.println("Pozisyonlar (" + s.positions.size() + "/" + maxPositions + "):");
        for (Position p : s.positions) {
            out.println("  " + p.symbol() + " " + p.lots() + " lot @ " + p.avgCost());
        }
    }

    /**
     * {@code confirm} sonuç satırını ve varsa hata listesini yazar.
     *
     * @param applied uygulanan işlem sayısı
     * @param errors  toplanan hata satırları (boşsa yalnızca özet yazılır)
     */
    public void renderConfirmSummary(int applied, List<String> errors) {
        out.println("İşlemler uygulandı: " + applied);
        if (!errors.isEmpty()) {
            out.println("Hatalar:");
            errors.forEach(out::println);
        }
    }

    /**
     * Hata listesini {@code "Hatalar:"} başlığı altında yazar.
     *
     * @param errors yazılacak hata satırları
     */
    public void renderErrors(List<String> errors) {
        out.println("Hatalar:");
        errors.forEach(out::println);
    }

    /**
     * {@code train} başlangıç satırını yazar.
     *
     * @param model    model türü
     * @param analysis analiz tipi
     * @param index    endeks adı
     */
    public void renderTrainStart(Object model, Object analysis, String index) {
        out.println("Model egitimi (" + model + ", analiz=" + analysis + ", endeks=" + index + ") yapiliyor...");
    }

    /**
     * {@code train} bitiş satırını yazar.
     *
     * @param modelKey    model anahtarı
     * @param analysisKey analiz tipi anahtarı
     */
    public void renderTrainDone(String modelKey, String analysisKey) {
        out.println("Model egitildi (bellekte): " + modelKey + " (" + analysisKey + ")");
    }
}