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
import java.util.Comparator;

/**
 * Portföy durumunu yöneten servis.
 * <p>
 * Portföy verileri YAML formatında diskte saklanır ({@code state.yaml}).
 * Bütçe yönetimi, pozisyon ekleme/çıkarma, validasyon ve maksimum pozisyon
 * limiti (5) gibi işlemleri sağlar. Tüm işlemler thread-safe'tir.
 */
@Service
public class PortfolioService {
    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);
    private static final int MAX_POSITIONS = 5;

    private final AppConfig appConfig;
    private final ObjectMapper yamlMapper;
    private PortfolioState state;

    /**
     * {@code PortfolioService} servisini kurar. YAML mapper'ı yapılandırır
     * ve mevcut state dosyasını yükler.
     *
     * @param appConfig uygulama yapılandırması
     */
    public PortfolioService(AppConfig appConfig) {
        this.appConfig = appConfig;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.findAndRegisterModules();
        this.state = load();
        sortPositions();
    }

    /**
     * Portföy durumunun savunmacı bir kopyasını döndürür (referans paylaşımını önler).
     *
     * @return {@link PortfolioState} kopyası
     */
    public synchronized PortfolioState getState() {
        PortfolioState copy = new PortfolioState();
        copy.budget = state.budget;
        copy.advisorMode = state.advisorMode;
        copy.modelType = state.modelType;
        copy.selectedIndex = state.selectedIndex;
        copy.lastRunDate = state.lastRunDate;
        copy.positions = new java.util.ArrayList<>();
        if (state.positions != null) {
            for (Position p : state.positions) {
                copy.positions.add(new Position(p.symbol(), p.lots(), p.avgCost()));
            }
        }
        return copy;
    }

    /**
     * Portföy durumunu günceller ve diske yazar.
     *
     * @param newState yeni portföy durumu
     */
    public synchronized void save(PortfolioState newState) {
        this.state = newState;
        sortPositions();
        write();
    }

    /**
     * Kullanılabilir nakit miktarını döndürür (bütçe, negatif olamaz).
     *
     * @param currentPrices güncel fiyatlar (şu an için sadece bütçe bazlı hesaplanır)
     * @return kullanılabilir nakit (TL)
     */
    public synchronized double availableCash(java.util.Map<String, Double> currentPrices) {
        return Math.max(0.0, state.budget);
    }

    /**
     * Yeni bir pozisyon eklenip eklenemeyeceğini kontrol eder.
     *
     * @return {@code true} eğer pozisyon sayısı maksimumun altındaysa
     */
    public synchronized boolean canAddPosition() {
        return state.positions != null && state.positions.size() < MAX_POSITIONS;
    }

    /**
     * Maksimum pozisyon sayısını döndürür.
     *
     * @return maksimum pozisyon (5)
     */
    public synchronized int maxPositions() { return MAX_POSITIONS; }

    /**
     * Satış işlemlerinden sonra doldurulabilecek pozisyon sayısını hesaplar.
     *
     * @param sellCount yapılacak satış sayısı
     * @return kalan pozisyon slot sayısı (0-5 arası)
     */
    public synchronized int buySlotsAfter(int sellCount) {
        int posCount = state.positions != null ? state.positions.size() : 0;
        return Math.clamp(MAX_POSITIONS - (posCount - sellCount), 0, MAX_POSITIONS);
    }

    /**
     * Mevcut yatırımcı modunu döndürür.
     *
     * @return {@link AdvisorMode} sabiti
     */
    public synchronized AdvisorMode advisorMode() { return AdvisorMode.fromLabel(state.advisorMode); }

    /**
     * Mevcut ML model türünü döndürür.
     *
     * @return {@link ModelType} sabiti
     */
    public synchronized ModelType modelType() { return ModelType.fromKey(state.modelType); }

    /**
     * Portföy durumuna atomik bir güncelleme uygular ve sonucu diske yazar.
     *
     * @param fn durumu değiştiren consumer fonksiyonu
     */
    public synchronized void updateState(java.util.function.Consumer<PortfolioState> fn) {
        fn.accept(state);
        sortPositions();
        write();
    }

    /**
     * Bir alım veya satım işlemini portföye uygular.
     * <ul>
     *   <li>AL: pozisyonu ekler veya mevcut pozisyonu büyütür, bütçeyi düşer</li>
     *   <li>SAT: pozisyonu kaldırır, bütçeyi artırır</li>
     * </ul>
     *
     * @param symbol hisse sembolü
     * @param action işlem türü (AL veya SAT)
     * @param lots   lot miktarı
     * @param price  işlem fiyatı
     * @return {@code true} işlem başarılıysa
     */
    public synchronized boolean applyTransaction(String symbol, String action, int lots, double price) {
        if (symbol == null || symbol.isBlank()) {
            log.warn("applyTransaction: sembol bos, islem atlandi");
            return false;
        }
        if (lots <= 0) {
            log.warn("applyTransaction: lot sayisi pozitif olmali ({}), islem atlandi", lots);
            return false;
        }
        if (price <= 0) {
            log.warn("applyTransaction: fiyat pozitif olmali ({}), islem atlandi", price);
            return false;
        }
        if (state.positions == null) state.positions = new java.util.ArrayList<>();

        String sym = symbol.trim().toUpperCase();

        if ("AL".equalsIgnoreCase(action.trim())) {
            double cost = lots * price;
            if (cost > state.budget) {
                log.warn("applyTransaction: yetersiz butce (ihtiyac={}, mevcut={})", cost, state.budget);
                return false;
            }
            var idx = -1;
            for (int i = 0; i < state.positions.size(); i++) {
                if (state.positions.get(i).symbol().equals(sym)) { idx = i; break; }
            }
            if (idx >= 0) {
                var old = state.positions.get(idx);
                int totalLots = old.lots() + lots;
                double newAvgCost = (old.avgCost() * old.lots() + price * lots) / totalLots;
                state.positions.set(idx, new Position(sym, totalLots, newAvgCost));
            } else {
                if (!canAddPosition()) {
                    log.warn("applyTransaction: maks {} pozisyon siniri, {} eklenemedi", MAX_POSITIONS, sym);
                    return false;
                }
                state.positions.add(new Position(sym, lots, price));
            }
            state.budget -= cost;
            sortPositions();
            return true;
        } else if ("SAT".equalsIgnoreCase(action.trim())) {
            boolean removed = state.positions.removeIf(p -> p.symbol().equals(sym));
            if (removed) {
                state.budget += lots * price;
                sortPositions();
                return true;
            } else {
                log.warn("applyTransaction: SAT istegi ama {} portfoyde bulunamadi", sym);
                return false;
            }
        } else {
            log.warn("applyTransaction: gecersiz islem ({})", action);
            return false;
        }
    }

    /**
     * Portföyün bütçe kısıtına uygunluğunu doğrular: toplam pozisyon maliyeti
     * bütçeyi aşmamalıdır.
     *
     * @return hata mesajı veya {@code null} (sorun yoksa)
     */
    public synchronized String validatePortfolio() {
        if (state.positions == null || state.positions.isEmpty()) return null;
        double totalCost = state.positions.stream()
                .mapToDouble(p -> p.lots() * p.avgCost()).sum();
        if (totalCost > state.budget) {
            return "Pozisyon maliyeti (" + String.format("%.0f", totalCost)
                    + ") butceyi (" + String.format("%.0f", state.budget) + ") asiyor";
        }
        return null;
    }

    private void sortPositions() {
        if (state.positions != null) {
            state.positions.sort(Comparator.comparing(Position::symbol));
        }
    }

    /**
     * State dosyasını YAML'dan okur. Dosya yoksa veya okuma hatası olursa
     * boş bir {@link PortfolioState} döndürür.
     *
     * @region veri yükleme
     */
    private PortfolioState load() {
        File f = new File(appConfig.stateFile());
        if (!f.exists()) return new PortfolioState();
        try {
            PortfolioState s = yamlMapper.readValue(f, PortfolioState.class);
            if (s.positions != null) {
                s.positions.sort(Comparator.comparing(Position::symbol));
            }
            return s;
        } catch (IOException e) {
            log.warn("state.yaml okunamadi, bos baslatiliyor: {}", e.getMessage());
            return new PortfolioState();
        }
    }

    /**
     * Portföy durumunu YAML formatında diske yazar.
     */
    private void write() {
        try {
            yamlMapper.writeValue(new File(appConfig.stateFile()), state);
        } catch (IOException e) {
            log.error("state.yaml yazilamadi: {}", e.getMessage());
        }
    }
}
