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
 * Portföyün bellek içi sahibi ve durum dosyası (state.yaml) kalıcılığından sorumlu servis.
 * <p>
 * Durum {@link PortfolioState} olarak Jackson ({@code YAMLFactory}, modüller otomatik
 * kayıtlı) ile YAML biçiminde okunur ve yazılır. Erişim metodları {@code synchronized}
 * olduğundan servis thread-safe'tir ve {@link #getState} derin kopya döndürerek dışarıya
 * gerçek durum sızdırmaz.
 * <p>
 * Eşik ve sabitler: en fazla <strong>5 pozisyon</strong> taşınabilir; yüzen nokta
 * karşılaştırmalarında <strong>0.01 TL tolerans</strong> kullanılır. Alımlarda ağırlıklı
 * ortalama maliyet uygulanır; tam satımda pozisyon silinir ve brüt tutar nakde eklenir,
 * böylece gerçekleşen kâr/zarar nakde yansır.
 */
@Service
public class PortfolioService {
    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);
    private static final int MAX_POSITIONS = 5;

    private static final double TOLERANCE = 0.01;

    private final AppConfig appConfig;
    private final ObjectMapper yamlMapper;
    private PortfolioState state;

    /**
     * Servisi kurar ve portföy durumunu diskten yükler.
     * <p>
     * Durum dosyası (state.yaml) yoksa boş portföyle başlanır; okuma hatasında boş
     * portföyle devam edilip uyarı loglanır. Yüklenen pozisyonlar sembole göre sıralanır.
     *
     * @param appConfig durum dosyası yolunu içeren uygulama yapılandırması
     */
    public PortfolioService(AppConfig appConfig) {
        this.appConfig = appConfig;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.findAndRegisterModules();
        this.state = load();
        sortPositions();
    }

    /**
     * Portföy durumunun derin kopyasını döndürür.
     * <p>
     * Pozisyonlar da tek tek kopyalandığı için dışarıya gerçek durum sızdırılmaz.
     *
     * @return kopyalanmış {@link PortfolioState}
     * @implNote Kopyalama, çağıranın bellekteki durumu değiştirmesini engeller.
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
     * Serbest nakdi döndürür.
     *
     * @return serbest nakit (TL)
     */
    public synchronized double availableCash() {
        return state.cash;
    }

    /**
     * Pozisyonlara yatırılan toplam maliyeti döndürür.
     *
     * @return yatırılan toplam maliyet (Σ lot × ortalama maliyet, TL)
     */
    public synchronized double investedCost() {
        return investedCost(state);
    }

    /**
     * Verilen durum için yatırılan toplam maliyeti hesaplar.
     *
     * @param s maliyeti hesaplanacak portföy durumu
     * @return yatırılan toplam maliyet (Σ lot × ortalama maliyet, TL); pozisyon yoksa 0
     */
    private static double investedCost(PortfolioState s) {
        if (s.positions == null) return 0.0;
        double total = 0.0;
        for (Position p : s.positions) {
            if (p != null) total += p.lots() * p.avgCost();
        }
        return total;
    }

    /**
     * Yeni pozisyon eklenebilirliğini belirler.
     *
     * @return 5 pozisyon sınırı dolu değilse {@code true}
     */
    public synchronized boolean canAddPosition() {
        return state.positions != null && state.positions.size() < MAX_POSITIONS;
    }

    /**
     * Taşınabilecek en fazla pozisyon sayısını döndürür.
     *
     * @return en fazla 5 pozisyon
     */
    public synchronized int maxPositions() { return MAX_POSITIONS; }

    /**
     * Satışlardan sonra açılacak boş slot sayısını hesaplar.
     *
     * @param sellCount beklenen satış adedi
     * @return satıştan sonra açılacak slot sayısı (0 ile 5 arasında sınırlanır)
     */
    public synchronized int buySlotsAfter(int sellCount) {
        int posCount = state.positions != null ? state.positions.size() : 0;
        return Math.clamp(MAX_POSITIONS - (posCount - sellCount), 0, MAX_POSITIONS);
    }

    /**
     * Durumdaki danışman modunu enum'a çözer.
     *
     * @return çözümlenen {@link AdvisorMode}; değer geçersizse varsayılan mod
     */
    public synchronized AdvisorMode advisorMode() { return AdvisorMode.fromLabel(state.advisorMode); }

    /**
     * Durumdaki model tipini enum'a çözer.
     *
     * @return çözümlenen {@link ModelType}; değer geçersizse varsayılan model
     */
    public synchronized ModelType modelType() { return ModelType.fromKey(state.modelType); }

    /**
     * Durum üzerinde verilen değişikliği uygular, pozisyonları sıralar ve diske yazar.
     * <p>
     * Örneğin {@code lastRunDate} güncellemesi için kullanılır.
     *
     * @param fn duruma uygulanacak değişiklik
     * @throws RuntimeException verilen işlev bir istisna fırlatırsa olduğu gibi yayılır
     */
    public synchronized void updateState(java.util.function.Consumer<PortfolioState> fn) {
        fn.accept(state);
        sortPositions();
        write();
    }

    /**
     * Portföyü bütçe ve parametrelerle başlatır.
     * <p>
     * Null parametreler eskisini korur. Pozisyonlar kopyalanarak alınır; nakit,
     * {@code bütçe − yatırılan maliyet} olarak yeniden hesaplanır ve durum yazılır.
     *
     * @param budget      başlangıç sermayesi (TL)
     * @param advisorMode danışman modu adı (null ise eskisi korunur)
     * @param modelType   model adı (null ise eskisi korunur)
     * @param positions   açılacak pozisyonlar (kopyalanır)
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
     * Portföyü kısmi olarak günceller ve yeni durumu kaydeder.
     * <p>
     * Bütçe farkı nakde eklenir/çıkarılır (mutlak fark 0.01 TL toleransın altındaysa
     * nakde dokunulmaz). Yeni pozisyon listesi verilirse eski yatırılan maliyet ile
     * yenisi arasındaki fark nakde işlenir: pozisyon eklemek nakdi maliyeti kadar
     * azaltır, çıkarmak artırır. Mod, model ve endeks null değilse güncellenir.
     *
     * @param budget      yeni bütçe (TL)
     * @param advisorMode danışman modu adı (null ise korunur)
     * @param modelType   model adı (null ise korunur)
     * @param indexName   endeks adı (null ise korunur)
     * @param positions   yeni pozisyon listesi (null ise korunur; kopyalanır)
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
     * Tek bir AL/SAT işlemini doğrulayıp uygular.
     * <p>
     * Ortak geçerlilik kontrolleri: sembol boş olamaz, lot > 0, fiyat sonlu ve > 0.
     * <strong>AL</strong> — maliyet (lot × fiyat), nakit + 0.01 TL toleransı aşarsa işlem
     * reddedilir (uyarı loglanır). Mevcut pozisyona alım AĞIRLIKLI ORTALAMA MALİYET ile
     * birleştirilir: {@code yeni maliyet = (eski maliyet × eski lot + fiyat × lot) / toplam lot}.
     * Yeni sembolde en fazla 5 pozisyon slot sınırı uygulanır; maliyet nakitten düşülür.
     * <strong>SAT</strong> — pozisyon yoksa veya lot yetmezse reddedilir. Tam satımda
     * pozisyon silinir; kısmi satımda lot azalır ve ortalama maliyet DEĞİŞMEZ. Brüt tutar
     * (lot × fiyat) nakde eklenir; böylece gerçekleşen kâr/zarar nakde yansır.
     * Geçerli işlemde pozisyonlar sıralanır ve durum diske yazılır.
     *
     * @param symbol hisse sembolü
     * @param action işlem türü ({@code AL} veya {@code SAT})
     * @param lots   işlem lot sayısı
     * @param price  işlem fiyatı (TL)
     * @return işlem uygulandıysa {@code true}; geçersiz veya reddedildiyse {@code false}
     * @implNote Geçersiz eylem adı (AL/SAT dışında) da {@code false} ile sonuçlanır.
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
     * Portföy kurallarını tarar ve ihlalleri bildirir.
     * <p>
     * İki kural denetlenir: negatif nakit (−0.01 TL toleransın altındaki değerler) ve
     * 5 pozisyon sınırının aşılması.
     *
     * @return sorun yoksa {@code null}; varsa {@code " | "} ile birleşik uyarı metni
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

    /**
     * TL tutarını gösterim için yuvarlar.
     *
     * @param v biçimlenecek tutar
     * @return ondalıksız biçimlenmiş metin ({@code %.0f})
     */
    private static String fmt(double v) {
        return String.format("%.0f", v);
    }

    /**
     * Pozisyonları sembole göre alfabetik sıralar.
     * <p>
     * Bu sayede durum dosyası (state.yaml) deterministik biçimde yazılır.
     */
    private void sortPositions() {
        if (state.positions != null) {
            state.positions.sort(Comparator.comparing(Position::symbol));
        }
    }

    /**
     * Portföy durumunu durum dosyasından (state.yaml) okur.
     * <p>
     * Dosya yoksa boş portföy döner; okuma hatasında boş portföyle devam edilip uyarı
     * loglanır. Dönüşte pozisyonlar sembole göre sıralanır.
     *
     * @return diskten yüklenen (veya boş) {@link PortfolioState}
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
     * Portföy durumunu durum dosyasına (state.yaml) yazar.
     * <p>
     * Yazma hatası yalnızca loglanır, çağıran koda yayılmaz.
     */
    private void write() {
        try {
            yamlMapper.writeValue(new File(appConfig.stateFile()), state);
        } catch (IOException e) {
            log.error("state.yaml yazilamadi: {}", e.getMessage());
        }
    }
}