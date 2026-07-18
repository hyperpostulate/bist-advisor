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
        // args: "THYAO,AL,100,240.5" "ASELS,SAT,50,351"
        for (String a : args) {
            String[] p = a.split(",");
            if (p.length >= 4) {
                portfolioService.applyTransaction(
                        p[0].trim(), p[1].trim(),
                        Integer.parseInt(p[2].trim()), Double.parseDouble(p[3].trim()));
            }
        }
        portfolioService.save(portfolioService.getState());
        System.out.println("İşlemler uygulandı: " + args.size());
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
        for (Recommendation x : r.holdings()) {
            System.out.println(x.index() + ") " + x.symbol() + " " + x.lots() + " lot | " + x.action() + " | " + x.note());
        }
        System.out.println("-- Al Önerileri --");
        if (r.buys().isEmpty()) System.out.println("(Al önerisi yok)");
        for (Recommendation x : r.buys()) {
            System.out.println(x.index() + ") " + x.symbol() + " " + x.lots() + " lot @ " + x.price() + " | skor=" + String.format("%.2f", x.score()));
        }
        System.out.println("* Yatırım tavsiyesi değildir.");
    }
}
