package org.mesutormanli.bistadvisor.cli;

import org.mesutormanli.bistadvisor.advisor.DailyAdvisor;
import org.mesutormanli.bistadvisor.advisor.DailyAdvisor.AnalysisResult;
import org.mesutormanli.bistadvisor.advisor.DailyAdvisor.Recommendation;
import org.mesutormanli.bistadvisor.config.AdvisorMode;
import org.mesutormanli.bistadvisor.config.ModelType;
import org.mesutormanli.bistadvisor.model.ModelTrainer;
import org.mesutormanli.bistadvisor.portfolio.PortfolioService;
import org.mesutormanli.bistadvisor.portfolio.PortfolioState;
import org.mesutormanli.bistadvisor.portfolio.Position;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AdvisorCommands {

    private final PortfolioService portfolioService;
    private final DailyAdvisor dailyAdvisor;
    private final ModelTrainer modelTrainer;

    /**
     * {@code AdvisorCommands} servisini kurar. Bağımlılıklar Spring tarafından enjekte edilir.
     */
    public AdvisorCommands(PortfolioService portfolioService, DailyAdvisor dailyAdvisor, ModelTrainer modelTrainer) {
        this.portfolioService = portfolioService;
        this.dailyAdvisor = dailyAdvisor;
        this.modelTrainer = modelTrainer;
    }

    /**
     * Portföyü ilklendirir: toplam sermaye, yatırım modu, model türü ve başlangıç pozisyonlarını atar.
     * Nakit, sermayeden pozisyon maliyetleri düşülerek kurulur.
     *
     * @param budget   toplam sermaye katkısı (TL)
     * @param mode     yatırım modu etiketi (TEMKINLI/DENGELI/AGRESIF)
     * @param model    ML model anahtarı (random_forest/svm/knn)
     * @param positions başlangıç pozisyonları listesi
     */
    public void init(double budget, String mode, String model, List<Position> positions) {
        String modeName = mode != null
                ? AdvisorMode.fromLabel(mode).name() : portfolioService.advisorMode().name();
        String modelName = model != null
                ? ModelType.fromKey(model).name() : portfolioService.modelType().name();
        portfolioService.initPortfolio(budget, modeName, modelName, positions);
        String validationError = portfolioService.validatePortfolio();
        if (validationError != null) {
            System.out.println("Uyari: " + validationError);
        }
        System.out.println("Portföy kaydedildi: sermaye=" + budget + " TL, nakit="
                + Math.round(portfolioService.availableCash()) + " TL, mod=" + modeName
                + ", model=" + modelName);
    }

    /**
     * Günlük analizi çalıştırır ve sonuçları konsola yazdırır.
     */
    public void run() {
        AnalysisResult r = dailyAdvisor.analyze();
        print(r);
    }

    /**
     * Kullanıcı tarafından onaylanan işlemleri portföye uygular.
     * Her işlem {@code SEMBOL,AL/SAT,lot,fiyat} formatında olmalıdır.
     *
     * @param args onaylanan işlem listesi
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
     * Portföyün mevcut durumunu konsola yazdırır (toplam sermaye, nakit, mod, model, pozisyonlar).
     */
    public void status() {
        PortfolioState s = portfolioService.getState();
        double cash = portfolioService.availableCash();
        System.out.println("Sermaye: " + Math.round(s.budget) + " TL | Nakit: " + Math.round(cash) + " TL");
        System.out.println("Mod: " + s.advisorMode + " | Model: " + s.modelType);
        System.out.println("Pozisyonlar (" + s.positions.size() + "/" + portfolioService.maxPositions() + "):");
        for (Position p : s.positions) {
            System.out.println("  " + p.symbol() + " " + p.lots() + " lot @ " + p.avgCost());
        }
    }

    /**
     * Seçili modeli ve endeksi kullanarak ML modelini eğitir ve bellekte saklar.
     */
    public void train() {
        ModelType t = portfolioService.modelType();
        String idx = portfolioService.getState().selectedIndex;
        System.out.println("Model egitimi (" + t + ", endeks=" + idx + ") yapiliyor...");
        modelTrainer.train(t, idx);
        System.out.println("Model egitildi (bellekte): " + t.key);
    }

    /**
     * Analiz sonucunu konsola formatlı biçimde yazdırır.
     *
     * @param r analiz sonucu
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
