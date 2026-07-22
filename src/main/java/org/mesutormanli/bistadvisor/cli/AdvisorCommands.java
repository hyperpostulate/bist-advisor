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

/**
 * CLI komutlari: init / run / confirm / status / train.
 * Web argumani verilmeden calistirildiginda devreye girer.
 */
@Component
public class AdvisorCommands {

    private final PortfolioService portfolioService;
    private final DailyAdvisor dailyAdvisor;
    private final ModelTrainer modelTrainer;

    public AdvisorCommands(PortfolioService portfolioService, DailyAdvisor dailyAdvisor, ModelTrainer modelTrainer) {
        this.portfolioService = portfolioService;
        this.dailyAdvisor = dailyAdvisor;
        this.modelTrainer = modelTrainer;
    }

    public void init(double budget, String mode, String model, List<Position> positions) {
        PortfolioState state = portfolioService.getState();
        state.budget = budget;
        if (mode != null) state.advisorMode = AdvisorMode.fromLabel(mode).name();
        if (model != null) state.modelType = ModelType.fromKey(model).name();
        if (positions != null) state.positions = new ArrayList<>(positions);
        portfolioService.save(state);
        System.out.println("Portföy kaydedildi: bütçe=" + budget + ", mod=" + state.advisorMode + ", model=" + state.modelType);
    }

    public void run() {
        AnalysisResult r = dailyAdvisor.analyze();
        print(r);
    }

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
                portfolioService.applyTransaction(
                        p[0].trim(), action,
                        Integer.parseInt(p[2].trim()), Double.parseDouble(p[3].trim()));
                applied++;
            } catch (NumberFormatException e) {
                errors.add("Gecersiz sayi: " + a);
            }
        }
        portfolioService.save(portfolioService.getState());
        System.out.println("İşlemler uygulandı: " + applied);
        if (!errors.isEmpty()) {
            System.out.println("Hatalar:");
            errors.forEach(System.out::println);
        }
    }

    public void status() {
        PortfolioState s = portfolioService.getState();
        System.out.println("Bütçe: " + s.budget + " TL");
        System.out.println("Mod: " + s.advisorMode + " | Model: " + s.modelType);
        System.out.println("Pozisyonlar (" + s.positions.size() + "/5):");
        for (Position p : s.positions) {
            System.out.println("  " + p.symbol + " " + p.lots + " lot @ " + p.avgCost);
        }
    }

    public void train() {
        ModelType t = portfolioService.modelType();
        String idx = portfolioService.getState().selectedIndex;
        System.out.println("Model egitimi (" + t + ", endeks=" + idx + ") yapiliyor...");
        modelTrainer.train(t, idx);
        System.out.println("Model egitildi (bellekte): " + t.key);
    }

    private void print(AnalysisResult r) {
        System.out.println("=== Günlük Öneri (" + r.positionCount() + "/5) | Nakit: " + Math.round(r.availableCash()) + " TL ===");
        System.out.println("-- Mevcut Portföy --");
        java.util.Map<String, Position> posMap = new java.util.HashMap<>();
        for (Position p : portfolioService.getState().positions) posMap.put(p.symbol, p);
        for (Recommendation x : r.holdings()) {
            Position p = posMap.get(x.symbol());
            double pnlTl = p != null ? (x.price() - p.avgCost) * x.lots() : 0;
            System.out.println(x.index() + ") " + x.symbol() + " " + x.lots() + " lot | " + x.action()
                    + " | " + x.note() + " | " + String.format("%,.0f", pnlTl) + " TL");
        }
        System.out.println("-- Al Önerileri --");
        if (r.buys().isEmpty()) System.out.println("(Al önerisi yok)");
        for (Recommendation x : r.buys()) {
            System.out.println(x.index() + ") " + x.symbol() + " " + x.lots() + " lot @ " + x.price() + " | skor=" + String.format("%.2f", x.score()));
        }
        System.out.println("* Yatırım tavsiyesi değildir.");
    }
}
