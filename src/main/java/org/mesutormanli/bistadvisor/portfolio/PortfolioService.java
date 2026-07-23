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

/** state.yaml okuma/yazma, portfoy kisitlari (maks 5 pozisyon, butce kontrolu) ve islem uygulama. */
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

    /** Portfoy durumunun savunmacı bir kopyasini dondurur. */
    public synchronized PortfolioState getState() {
        PortfolioState copy = new PortfolioState();
        copy.budget = state.budget;
        copy.advisorMode = state.advisorMode;
        copy.modelType = state.modelType;
        copy.selectedIndex = state.selectedIndex;
        copy.lastRunDate = state.lastRunDate;
        if (state.positions != null) {
            copy.positions = new java.util.ArrayList<>();
            for (Position p : state.positions) {
                copy.positions.add(new Position(p.symbol, p.lots, p.avgCost));
            }
        }
        return copy;
    }

    /** Verilen state'i kaydeder (hafiza + dosya). */
    public synchronized void save(PortfolioState newState) {
        this.state = newState;
        write();
    }

    /** Kullanilabilir nakit = butce - pozisyonlarin guncel piyasa degeri. */
    public synchronized double availableCash(java.util.Map<String, Double> currentPrices) {
        if (state.positions == null) return state.budget;
        double invested = state.positions.stream()
                .mapToDouble(p -> p.lots * currentPrices.getOrDefault(p.symbol, p.avgCost)).sum();
        return Math.max(0.0, state.budget - invested);
    }

    /** Yeni pozisyon eklenebilir mi? (max 5 kontrolu). */
    public synchronized boolean canAddPosition() {
        return state.positions != null && state.positions.size() < MAX_POSITIONS;
    }

    /** Maksimum pozisyon sayisi (5). */
    public synchronized int maxPositions() { return MAX_POSITIONS; }

    /** SAT sonrasi kalan slotlar dahil, alim icin kullanilabilir maksimum yeni pozisyon sayisi. */
    public synchronized int buySlotsAfter(int sellCount) {
        int posCount = state.positions != null ? state.positions.size() : 0;
        return Math.max(0, Math.min(MAX_POSITIONS - (posCount - sellCount), MAX_POSITIONS));
    }

    /** Mevcut pozisyon sayisi. */
    public synchronized int currentPositionCount() {
        return state.positions != null ? state.positions.size() : 0;
    }

    /** state'teki advisorMode alanini AdvisorMode enum'ina cevirir. */
    public synchronized AdvisorMode advisorMode() { return AdvisorMode.fromLabel(state.advisorMode); }

    /** state'teki modelType alanini ModelType enum'ina cevirir. */
    public synchronized ModelType modelType() { return ModelType.fromKey(state.modelType); }

    /** Atomik read-modify-write: callback ile state mutate edilir, sonra kaydedilir. */
    public synchronized void updateState(java.util.function.Consumer<PortfolioState> fn) {
        fn.accept(state);
        write();
    }

    /** Gerceklesen AL/SAT islemini portfoye yansitir. */
    public synchronized void applyTransaction(String symbol, String action, int lots, double price) {
        if (symbol == null || symbol.isBlank()) {
            log.warn("applyTransaction: sembol bos, islem atlandi");
            return;
        }
        if (lots <= 0) {
            log.warn("applyTransaction: lot sayisi pozitif olmali ({}), islem atlandi", lots);
            return;
        }
        if (price <= 0) {
            log.warn("applyTransaction: fiyat pozitif olmali ({}), islem atlandi", price);
            return;
        }
        if (state.positions == null) state.positions = new java.util.ArrayList<>();

        Position p = state.positions.stream()
                .filter(x -> x.symbol.equalsIgnoreCase(symbol.trim()))
                .findFirst().orElse(null);

        if ("AL".equalsIgnoreCase(action.trim())) {
            if (p != null) {
                int addLots = lots;
                int totalLots = p.lots + addLots;
                p.avgCost = (p.avgCost * p.lots + price * addLots) / totalLots;
                p.lots = totalLots;
            } else {
                if (canAddPosition()) {
                    state.positions.add(new Position(symbol.trim().toUpperCase(), lots, price));
                } else {
                    log.warn("applyTransaction: maks {} pozisyon siniri, {} eklenemedi", MAX_POSITIONS, symbol);
                }
            }
        } else if ("SAT".equalsIgnoreCase(action.trim())) {
            if (p != null) {
                state.positions.remove(p);
            } else {
                log.warn("applyTransaction: SAT istegi ama {} portfoyde bulunamadi", symbol);
            }
        } else {
            log.warn("applyTransaction: gecersiz islem ({})", action);
        }
    }

    /** state.yaml dosyasindan portfoy durumunu okur, yoksa/yazilamazsa bos state ile baslar. */
    private PortfolioState load() {
        File f = new File(appConfig.stateFile());
        if (!f.exists()) return new PortfolioState();
        try {
            return yamlMapper.readValue(f, PortfolioState.class);
        } catch (IOException e) {
            log.warn("state.yaml okunamadi, bos baslatiliyor: {}", e.getMessage());
            return new PortfolioState();
        }
    }

    /** state.yaml dosyasina portfoy durumunu yazar. */
    private void write() {
        try {
            yamlMapper.writeValue(new File(appConfig.stateFile()), state);
        } catch (IOException e) {
            log.error("state.yaml yazilamadi: {}", e.getMessage());
        }
    }
}
