package org.mesutormanli.bistadvisor.portfolio;

import org.mesutormanli.bistadvisor.config.AdvisorMode;
import org.mesutormanli.bistadvisor.config.AppConfig;
import org.mesutormanli.bistadvisor.config.ModelType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * state.yaml okuma/yazma ve portfoy kisitlari (maks 5 hisse, bakiye).
 */
@Service
public class PortfolioService {
    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);
    private static final int MAX_POSITIONS = 5;

    private final AppConfig appConfig;
    private final ObjectMapper yamlMapper;
    private PortfolioState state;

    public PortfolioService(AppConfig appConfig) {
        this.appConfig = appConfig;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.findAndRegisterModules();
        this.state = load();
    }

    public PortfolioState getState() {
        return state;
    }

    public void save(PortfolioState newState) {
        this.state = newState;
        write();
    }

    /** Kullanilabilir nakit = butce - mevcut pozisyon degerleri (guncel fiyat map ile). */
    public double availableCash(java.util.Map<String, Double> currentPrices) {
        double invested = state.positions.stream()
                .mapToDouble(p -> p.lots * currentPrices.getOrDefault(p.symbol, p.avgCost))
                .sum();
        return Math.max(0.0, state.budget - invested);
    }

    public boolean canAddPosition() {
        return state.positions.size() < MAX_POSITIONS;
    }

    public int maxPositions() {
        return MAX_POSITIONS;
    }

    /** SAT sonrasi acilan slot da dahil, alim icin kullanilabilir maksimum yeni pozisyon. */
    public int buySlotsAfter(int sellCount) {
        int remaining = MAX_POSITIONS - (state.positions.size() - sellCount);
        return Math.max(0, Math.min(remaining, MAX_POSITIONS));
    }

    public int currentPositionCount() {
        return state.positions.size();
    }

    public AdvisorMode advisorMode() {
        return AdvisorMode.fromLabel(state.advisorMode);
    }

    public ModelType modelType() {
        return ModelType.fromKey(state.modelType);
    }

    /** Gerceklesen islemi state'e isler (web onay kutusundan gelir). */
    public void applyTransaction(String symbol, String action, int lots, double price) {
        Position p = state.positions.stream()
                .filter(x -> x.symbol.equalsIgnoreCase(symbol))
                .findFirst().orElse(null);

        if ("AL".equalsIgnoreCase(action)) {
            if (p != null) {
                // Mevcut pozisyona ekleme: agirlikli ortalama maliyet guncellenir
                int addLots = lots > 0 ? lots : (int) Math.floor(state.budget / price);
                if (addLots > 0) {
                    int totalLots = p.lots + addLots;
                    p.avgCost = (p.avgCost * p.lots + price * addLots) / totalLots;
                    p.lots = totalLots;
                }
            } else {
                // Yeni pozisyon ac (maks 5 siniri)
                if (canAddPosition() && lots > 0 && price > 0) {
                    state.positions.add(new Position(symbol.toUpperCase(), lots, price));
                }
            }
        } else if ("SAT".equalsIgnoreCase(action)) {
            if (p != null) {
                int sellLots = lots > 0 ? Math.min(lots, p.lots) : p.lots;
                p.lots -= sellLots;
                if (p.lots <= 0) state.positions.remove(p);
            }
        }
    }

    private PortfolioState load() {
        File f = new File(appConfig.stateFile());
        if (!f.exists()) {
            return new PortfolioState();
        }
        try {
            return yamlMapper.readValue(f, PortfolioState.class);
        } catch (IOException e) {
            log.warn("state.yaml okunamadi, bos baslatiliyor: {}", e.getMessage());
            return new PortfolioState();
        }
    }

    private void write() {
        try {
            yamlMapper.writeValue(new File(appConfig.stateFile()), state);
        } catch (IOException e) {
            log.error("state.yaml yazilamadi: {}", e.getMessage());
        }
    }
}
