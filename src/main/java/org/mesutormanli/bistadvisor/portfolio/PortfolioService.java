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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Portföy durumunu yöneten servis.
 * <p>
 * <strong>Nakit modeli:</strong> {@code state.cash} alanında <em>açık nakit</em>, {@code state.budget}
 * alanında <em>toplam sermaye katkısı</em> tutulur. Alım/satım işlemleri nakdi doğrudan
 * değiştirir: alımda {@code nakit -= lot × alış fiyatı}, satımda
 * {@code nakit += lot × satış fiyatı}. Böylece <strong>gerçekleşen kâr/zarar nakde yansır</strong>:
 * kârlı satış kullanıcıyı zenginleştirir, zararlı satış fakirleştirir.
 * Toplam değer (equity) = {@code nakit + Σ lot × güncel fiyat}.
 * <p>
 * Portföy verileri YAML formatında diskte saklanır ({@code state.yaml}). Nakit kontrolü,
 * pozisyon ekleme/çıkarma, kısmi satış, validasyon ve maksimum pozisyon limiti (5) gibi
 * işlemleri sağlar. Tüm işlemler thread-safe'tir ve başarılı işlemler anında diske yazılır.
 */
@Service
public class PortfolioService {
    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);
    private static final int MAX_POSITIONS = 5;

    /** TL cinsinden mutabakat toleransı (kuruşluk yuvarlama farkları için). */
    private static final double TOLERANCE = 0.01;

    private final AppConfig appConfig;
    private final ObjectMapper yamlMapper;
    private PortfolioState state;

    /**
     * {@code PortfolioService} servisini kurar. YAML mapper'ı yapılandırır ve
     * mevcut state dosyasını yükler.
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
        copy.cash = state.cash;
        copy.advisorMode = state.advisorMode;
        copy.modelType = state.modelType;
        copy.selectedIndex = state.selectedIndex;
        copy.lastRunDate = state.lastRunDate;
        copy.positions = new ArrayList<>();
        if (state.positions != null) {
            for (Position p : state.positions) {
                copy.positions.add(new Position(p.symbol(), p.lots(), p.avgCost()));
            }
        }
        return copy;
    }

    /**
     * Kullanılabilir nakit (TL) döndürür.
     *
     * @return açık nakit bakiyesi
     */
    public synchronized double availableCash() {
        return state.cash;
    }

    /**
     * Pozisyonların toplam maliyet tabanını (Σ lot × ortalama maliyet) döndürür.
     *
     * @return yatırılan maliyet (TL)
     */
    public synchronized double investedCost() {
        return investedCost(state);
    }

    private static double investedCost(PortfolioState s) {
        if (s.positions == null) return 0.0;
        double total = 0.0;
        for (Position p : s.positions) {
            if (p != null) total += p.lots() * p.avgCost();
        }
        return total;
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
     * Satış işlemlerinden sonra doldurulabilecek <em>yeni</em> pozisyon sayısını hesaplar.
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
     * Nakit muhasebesi yapılmaz — yalnızca alan düzeyinde ince ayarlar içindir.
     *
     * @param fn durumu değiştiren consumer fonksiyonu
     */
    public synchronized void updateState(java.util.function.Consumer<PortfolioState> fn) {
        fn.accept(state);
        sortPositions();
        write();
    }

    /**
     * Portföyü sıfırdan ilkilendirir: sermaye, mod, model ve başlangıç pozisyonlarını
     * atar; nakdi {@code nakit = sermaye − Σ pozisyon maliyeti} olarak kurar.
     *
     * @param budget      toplam sermaye katkısı (TL)
     * @param advisorMode mod adı ({@code null} ise mevcut korunur)
     * @param modelType   model adı ({@code null} ise mevcut korunur)
     * @param positions   başlangıç pozisyonları ({@code null} ise boş)
     */
    public synchronized void initPortfolio(double budget, String advisorMode,
                                           String modelType, List<Position> positions) {
        state.budget = budget;
        if (advisorMode != null) state.advisorMode = advisorMode;
        if (modelType != null) state.modelType = modelType;
        state.positions = positions != null ? new ArrayList<>(positions) : new ArrayList<>();
        sortPositions();
        state.cash = budget - investedCost();
        write();
    }

    /**
     * Manuel portföy güncellemesini uygular ve nakdi mutabakata geçirir:
     * <ul>
     *   <li>Sermaye farkı para yatırma/çekme sayılır: {@code nakit += Δsermaye};</li>
     *   <li>Pozisyon değişikliği (ekleme/silme/düzeltme) maliyet farkı kadar nakdi
     *       kaydırır: {@code nakit += eski maliyet − yeni maliyet}. Pozisyon silme böylece
     *       maliyet bedeliyle satılmış gibi nakde döner; gerçekleşen kâr/zarar için
     *       işlem onayı ({@link #applyTransaction}) kullanılmalıdır.</li>
     * </ul>
     * Bu kurallar {@code nakit + maliyet = sermaye} değişmezini (gerçekleşen PnL hariç)
     * korur.
     *
     * @param budget      yeni sermaye ({@code <= 0} ise değişmez)
     * @param advisorMode mod adı ({@code null} ise değişmez)
     * @param modelType   model adı ({@code null} ise değişmez)
     * @param indexName   endeks adı ({@code null} ise değişmez)
     * @param positions   yeni pozisyon listesi ({@code null} ise değişmez)
     */
    public synchronized void updatePortfolio(double budget, String advisorMode, String modelType,
                                             String indexName, List<Position> positions) {
        double cash = state.cash;
        if (budget > 0 && Math.abs(budget - state.budget) > TOLERANCE) {
            cash += budget - state.budget;
            state.budget = budget;
        }
        if (positions != null) {
            double oldInvested = investedCost();
            state.positions = new ArrayList<>(positions);
            sortPositions();
            cash += oldInvested - investedCost();
        }
        if (advisorMode != null) state.advisorMode = advisorMode;
        if (modelType != null) state.modelType = modelType;
        if (indexName != null) state.selectedIndex = indexName;
        state.cash = cash;
        write();
    }

    /**
     * Bir alım veya satım işlemini portföye uygular ve sonucu anında diske yazar.
     * <ul>
     *   <li><strong>AL:</strong> {@code nakit -= lot × fiyat}; pozisyon eklenir veya
     *       mevcut pozisyon büyütülür (nakit yeterli olmalı)</li>
     *   <li><strong>SAT:</strong> {@code nakit += lot × fiyat}; kısmi satışta pozisyon
     *       küçülür, tam satışta kaldırır. Gerçekleşen kâr/zarar nakde yansır.</li>
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
        if (price <= 0 || !Double.isFinite(price)) {
            log.warn("applyTransaction: fiyat pozitif olmali ({}), islem atlandi", price);
            return false;
        }
        if (state.positions == null) state.positions = new ArrayList<>();

        String sym = symbol.trim().toUpperCase();
        double cash = state.cash;

        if ("AL".equalsIgnoreCase(action.trim())) {
            double cost = lots * price;
            if (cost > cash + TOLERANCE) {
                log.warn("applyTransaction: yetersiz nakit (ihtiyac={}, nakit={})", cost, cash);
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
            state.cash = cash - cost;
            sortPositions();
            write();
            return true;
        } else if ("SAT".equalsIgnoreCase(action.trim())) {
            var idx = -1;
            for (int i = 0; i < state.positions.size(); i++) {
                if (state.positions.get(i).symbol().equals(sym)) { idx = i; break; }
            }
            if (idx < 0) {
                log.warn("applyTransaction: SAT istegi ama {} portfoyde bulunamadi", sym);
                return false;
            }
            Position old = state.positions.get(idx);
            if (lots > old.lots()) {
                log.warn("applyTransaction: {} icin yetersiz lot (istenen={}, mevcut={})",
                        sym, lots, old.lots());
                return false;
            }
            if (lots == old.lots()) {
                state.positions.remove(idx);
            } else {
                state.positions.set(idx, new Position(sym, old.lots() - lots, old.avgCost()));
            }
            state.cash = cash + lots * price;
            sortPositions();
            write();
            return true;
        } else {
            log.warn("applyTransaction: gecersiz islem ({})", action);
            return false;
        }
    }

    /**
     * Portföyün nakit ve pozisyon kısıtlarına uygunluğunu doğrular: nakit negatif
     * olmamalı ve pozisyon sayısı limiti ({@value #MAX_POSITIONS}) aşılmamalıdır.
     *
     * @return uyarı mesajı veya {@code null} (sorun yoksa)
     */
    public synchronized String validatePortfolio() {
        List<String> errors = new ArrayList<>();
        double cash = state.cash;
        if (cash < -TOLERANCE) {
            errors.add("Negatif nakit (" + fmt(cash) + " TL)");
        }
        if (state.positions != null && state.positions.size() > MAX_POSITIONS) {
            errors.add("Pozisyon sayisi (" + state.positions.size() + ") siniri ("
                    + MAX_POSITIONS + ") asiyor");
        }
        return errors.isEmpty() ? null : String.join(" | ", errors);
    }

    private static String fmt(double v) {
        return String.format("%.0f", v);
    }

    private void sortPositions() {
        if (state.positions != null) {
            state.positions.sort(Comparator.comparing(Position::symbol));
        }
    }

    /**
     * State dosyasını YAML'dan okur. Dosya yoksa veya okuma hatası olursa boş bir
     * {@link PortfolioState} döndürür.
     *
     * @return yüklenmiş portföy durumu
     */
    private PortfolioState load() {
        File f = new File(appConfig.stateFile());
        PortfolioState s;
        if (!f.exists()) {
            s = new PortfolioState();
        } else {
            try {
                s = yamlMapper.readValue(f, PortfolioState.class);
            } catch (IOException e) {
                log.warn("state.yaml okunamadi, bos baslatiliyor: {}", e.getMessage());
                s = new PortfolioState();
            }
        }
        if (s.positions != null) {
            s.positions.sort(Comparator.comparing(Position::symbol));
        }
        return s;
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