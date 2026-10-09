package org.mesutormanli.bistadvisor.cli;

import org.mesutormanli.bistadvisor.advisor.DailyAdvisor;
import org.mesutormanli.bistadvisor.advisor.DailyAdvisor.AnalysisResult;
import org.mesutormanli.bistadvisor.advisor.DailyAdvisor.Recommendation;
import org.mesutormanli.bistadvisor.config.AdvisorMode;
import org.mesutormanli.bistadvisor.config.AnalysisType;
import org.mesutormanli.bistadvisor.config.ModelType;
import org.mesutormanli.bistadvisor.model.ModelTrainer;
import org.mesutormanli.bistadvisor.portfolio.PortfolioService;
import org.mesutormanli.bistadvisor.portfolio.PortfolioState;
import org.mesutormanli.bistadvisor.portfolio.Position;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Komut satırı (CLI) komutlarının uygulaması ve konsol çıktı biçimi.
 * <p>
 * {@code init}, {@code run}, {@code confirm}, {@code status} ve {@code train}
 * komutlarını karşılar; her komut sonucunu Türkçe özet/döküm olarak konsola yazar.
 * Kalıcılık {@link PortfolioService} üzerinden durum dosyası (state.yaml) ile sağlanır.
 */
@Component
public class AdvisorCommands {

    private final PortfolioService portfolioService;
    private final DailyAdvisor dailyAdvisor;
    private final ModelTrainer modelTrainer;

    /**
     * Komut uygulamasını bağımlılıklarıyla kurar.
     *
     * @param portfolioService portföyün bellek içi sahibi ve durum dosyası (state.yaml) kalıcılığı
     * @param dailyAdvisor     günlük analiz ve öneri üreticisi
     * @param modelTrainer     seçili modeli bellekte eğiten bileşen
     */
    public AdvisorCommands(PortfolioService portfolioService, DailyAdvisor dailyAdvisor, ModelTrainer modelTrainer) {
        this.portfolioService = portfolioService;
        this.dailyAdvisor = dailyAdvisor;
        this.modelTrainer = modelTrainer;
    }

    /**
     * {@code init} komutunu uygular: portföyü bütçe ve parametrelerle başlatır.
     * <p>
     * Verilmeyen mod/model/analiz tipi mevcut portföyden miras alınır. Önce
     * {@link PortfolioService#initPortfolio} çağrılır; doğrulama uyarısı varsa
     * "Uyari:" olarak basılır, ardından sermaye/nakit/mod/model/analiz tipi özet satırı
     * yazılır.
     *
     * @param budget     başlangıç sermayesi (TL)
     * @param mode       danışman modu etiketi (null ise mevcut mod miras alınır)
     * @param model      model anahtarı (null ise mevcut model miras alınır)
     * @param analysis   analiz tipi anahtarı (null ise mevcut analiz tipi miras alınır)
     * @param positions  açılacak pozisyon listesi
     */
    public void init(double budget, String mode, String model, String analysis, List<Position> positions) {
        String modeName = mode != null
                ? AdvisorMode.fromLabel(mode).name() : portfolioService.advisorMode().name();
        String modelName = model != null
                ? ModelType.fromKey(model).name() : portfolioService.modelType().name();
        String analysisName = analysis != null
                ? AnalysisType.fromKey(analysis).name() : portfolioService.analysisType().name();
        portfolioService.initPortfolio(budget, modeName, modelName, analysisName, positions);
        String validationError = portfolioService.validatePortfolio();
        if (validationError != null) {
            System.out.println("Uyari: " + validationError);
        }
        System.out.println("Portföy kaydedildi: sermaye=" + budget + " TL, nakit="
                + Math.round(portfolioService.availableCash()) + " TL, mod=" + modeName
                + ", model=" + modelName + ", analiz=" + analysisName);
    }

    /**
     * {@code run} komutunu uygular: taze analizi çalıştırıp raporu yazar.
     */
    public void run() {
        AnalysisResult r = dailyAdvisor.analyze();
        print(r);
    }

    /**
     * {@code confirm} komutunu uygular: işlemler ayrıştırılıp portföye işlenir.
     * <p>
     * Her argüman {@code SEMBOL,AL/SAT,lot,fiyat} biçimindedir. 4 alandan az alan,
     * geçersiz AL/SAT veya sayısal hata "Hatalar:" listesinde toplanır. Başarılı
     * işlemler {@link PortfolioService#applyTransaction} ile uygulanır ve sonunda
     * uygulanan işlem sayısı yazdırılır.
     *
     * @param args {@code SEMBOL,AL/SAT,lot,fiyat} biçimli işlem metinleri
     */
    public void confirm(List<String> args) {
        List<String> errors = new ArrayList<>();
        int applied = 0;
        for (String a : args) {
            String[] p = a.split(",");
            if (p.length < 4) {
                errors.add("Gecersiz format (beklenen: SEMBOL,AL/SAT,lot,fiyat): " + a);
                continue;
            }
            String action = p[1].trim().toUpperCase();
            if (!"AL".equals(action) && !"SAT".equals(action)) {
                errors.add("Gecersiz islem (" + action + "): " + a);
                continue;
            }
            try {
                if (portfolioService.applyTransaction(
                        p[0].trim(), action,
                        Integer.parseInt(p[2].trim()), Double.parseDouble(p[3].trim()))) {
                    applied++;
                } else {
                    errors.add("Islem basarisiz: " + a);
                }
            } catch (NumberFormatException e) {
                errors.add("Gecersiz sayi: " + a);
            }
        }
        System.out.println("İşlemler uygulandı: " + applied);
        if (!errors.isEmpty()) {
            System.out.println("Hatalar:");
            errors.forEach(System.out::println);
        }
    }

    /**
     * {@code status} komutunu uygular: portföy özetini konsola yazar.
     * <p>
     * Sermaye, nakit, mod ve modelin yanı sıra pozisyon listesi (sembol, lot,
     * ortalama maliyet) yazdırılır; en fazla 5 pozisyon gösterilir.
     */
    public void status() {
        PortfolioState s = portfolioService.getState();
        double cash = portfolioService.availableCash();
        System.out.println("Sermaye: " + Math.round(s.budget) + " TL | Nakit: " + Math.round(cash) + " TL");
        System.out.println("Mod: " + s.advisorMode + " | Model: " + s.modelType
                + " | Analiz: " + s.analysisType);
        System.out.println("Pozisyonlar (" + s.positions.size() + "/" + portfolioService.maxPositions() + "):");
        for (Position p : s.positions) {
            System.out.println("  " + p.symbol() + " " + p.lots() + " lot @ " + p.avgCost());
        }
    }

    /**
     * {@code train} komutunu uygular: seçili modeli seçili endeks ve analiz tipi için eğitir.
     * <p>
     * {@link ModelTrainer#train} çağrılır; model yalnızca bellekte tutulur.
     */
    public void train() {
        ModelType t = portfolioService.modelType();
        AnalysisType a = portfolioService.analysisType();
        String idx = portfolioService.getState().selectedIndex;
        System.out.println("Model egitimi (" + t + ", analiz=" + a + ", endeks=" + idx + ") yapiliyor...");
        modelTrainer.train(t, a, idx);
        System.out.println("Model egitildi (bellekte): " + t.key + " (" + a.key + ")");
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
     * @param r konsola yazdırılacak günlük analiz sonucu
     */
    private void print(AnalysisResult r) {
        System.out.println("=== Günlük Öneri (" + r.positionCount() + "/5) | Nakit: " + Math.round(r.availableCash()) + " TL ===");
        if (!r.warnings().isEmpty()) {
            r.warnings().forEach(w -> System.out.println("! " + w));
        }
        System.out.println("-- Mevcut Portföy --");
        java.util.Map<String, Position> posMap = new java.util.HashMap<>();
        for (Position p : portfolioService.getState().positions) posMap.put(p.symbol(), p);
        for (Recommendation x : r.holdings()) {
            Position p = posMap.get(x.symbol());
            if (x.price() == null) {
                System.out.println(x.index() + ") " + x.symbol() + " " + x.lots() + " lot | "
                        + x.action() + " | " + x.note());
                continue;
            }
            double pnlTl = p != null ? (x.price() - p.avgCost()) * x.lots() : 0;
            System.out.println(x.index() + ") " + x.symbol() + " " + x.lots() + " lot | " + x.action()
                    + " | " + x.note() + " | " + String.format("%,.0f", pnlTl) + " TL");
        }
        System.out.println("-- Al Önerileri --");
        if (r.buys().isEmpty()) System.out.println("(Al önerisi yok)");
        for (Recommendation x : r.buys()) {
            System.out.println(x.index() + ") " + x.symbol() + " " + x.lots() + " lot @ "
                    + String.format("%,.2f", x.price()) + " | skor=" + String.format("%.2f", x.score()));
        }
        System.out.println("* Yatırım tavsiyesi değildir.");
    }
}
