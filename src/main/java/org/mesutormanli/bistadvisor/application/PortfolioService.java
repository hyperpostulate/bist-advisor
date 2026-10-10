package org.mesutormanli.bistadvisor.application;

import org.mesutormanli.bistadvisor.domain.advisor.RiskProfile;
import org.mesutormanli.bistadvisor.domain.portfolio.Portfolio;
import org.mesutormanli.bistadvisor.domain.portfolio.PortfolioPolicy;
import org.mesutormanli.bistadvisor.domain.portfolio.PortfolioSnapshot;
import org.mesutormanli.bistadvisor.domain.portfolio.PortfolioValidator;
import org.mesutormanli.bistadvisor.domain.portfolio.Position;
import org.mesutormanli.bistadvisor.domain.portfolio.Transaction;
import org.mesutormanli.bistadvisor.domain.portfolio.TransactionAction;
import org.mesutormanli.bistadvisor.domain.portfolio.TransactionOutcome;
import org.mesutormanli.bistadvisor.domain.portfolio.ValidationResult;
import org.mesutormanli.bistadvisor.domain.settings.AdvisorSettings;
import org.mesutormanli.bistadvisor.domain.settings.AnalysisType;
import org.mesutormanli.bistadvisor.domain.settings.ModelType;
import org.mesutormanli.bistadvisor.ports.PortfolioStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Portföy uygulama servisi: para/pozisyon muhasebesini ({@link Portfolio}) ve
 * kullanıcı ayarlarını ({@link AdvisorSettings}) kalıcı depo ({@link PortfolioStateStore})
 * üzerinden yöneten ince fasad.
 * <p>
 * Bu sınıf I/O orkestrasyonu ve mutasyonların kalıcılaştırılmasından sorumludur;
 * kurallar domendedir: işlem mutasyonu {@link Portfolio}, doğrulama
 * {@link PortfolioValidator}, sınır/sabiter {@link PortfolioPolicy} içindedir.
 * Durum dosyası serileştirme biçimi {@link PortfolioSnapshot} ile korunur.
 * Erişim/metasyon metodları {@code synchronized} olduğundan örnek thread-safe'tir.
 */
@Service
public class PortfolioService {
    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);

    private final PortfolioStateStore store;
    private Portfolio portfolio;
    private AdvisorSettings settings;

    /**
     * Kurar: durumu depodan yükler ve alan nesnesi ile ayarlara ayırır.
     *
     * @param store portföy durumunun kalıcı deposu
     */
    public PortfolioService(PortfolioStateStore store) {
        this.store = store;
        PortfolioSnapshot s = store.load();
        this.portfolio = Portfolio.restore(s.budget, s.cash, s.positions);
        this.settings = toSettings(s);
    }

    /**
     * Disk anlık görüntüsünden ayarları çözer; geçersiz değerler varsayılana düşer.
     *
     * @param s yüklenen anlık görüntü
     * @return çözümlenmiş ayarlar
     */
    private static AdvisorSettings toSettings(PortfolioSnapshot s) {
        LocalDate lastRun = null;
        if (s.lastRunDate != null) {
            try {
                lastRun = LocalDate.parse(s.lastRunDate);
            } catch (Exception ignored) {
                log.warn("lastRunDate cozumlenemedi, yok sayildi: {}", s.lastRunDate);
            }
        }
        return new AdvisorSettings(
                RiskProfile.fromLabel(s.advisorMode),
                ModelType.fromKey(s.modelType),
                AnalysisType.fromKey(s.analysisType),
                s.selectedIndex != null ? s.selectedIndex : AdvisorSettings.DEFAULT_INDEX,
                lastRun);
    }

    /**
     * Durumun güncel anlık görüntüsünü (kopya) döndürür.
     *
     * @return disk biçimiyle aynı düzeni taşıyan anlık görüntü
     */
    public synchronized PortfolioSnapshot snapshot() {
        PortfolioSnapshot s = new PortfolioSnapshot();
        s.budget = portfolio.budget();
        s.cash = portfolio.availableCash();
        s.advisorMode = settings.riskProfile().name();
        s.modelType = settings.modelType().name();
        s.analysisType = settings.analysisType().name();
        s.selectedIndex = settings.selectedIndex();
        s.positions = new ArrayList<>(portfolio.positions());
        s.lastRunDate = settings.lastRunDate() != null ? settings.lastRunDate().toString() : null;
        return s;
    }

    /**
     * Pozisyonların kopyasını (sembole göre sıralı) döndürür.
     *
     * @return pozisyon listesi kopyası
     */
    public synchronized List<Position> positions() {
        return portfolio.positions();
    }

    /**
     * Serbest nakdi döndürür.
     *
     * @return serbest nakit (TL)
     */
    public synchronized double availableCash() {
        return portfolio.availableCash();
    }

    /**
     * Pozisyonlara yatırılan toplam maliyeti döndürür.
     *
     * @return yatırılan toplam maliyet (TL)
     */
    public synchronized double investedCost() {
        return portfolio.investedCost();
    }

    /**
     * Yeni pozisyon eklenebilirliğini belirler.
     *
     * @return slot sınırı dolu değilse {@code true}
     */
    public synchronized boolean canAddPosition() {
        return portfolio.canAddPosition();
    }

    /**
     * Taşınabilecek en fazla pozisyon sayısını döndürür.
     *
     * @return {@link PortfolioPolicy#MAX_POSITIONS}
     */
    public synchronized int maxPositions() {
        return portfolio.maxPositions();
    }

    /**
     * Satışlardan sonra açılacak boş slot sayısını hesaplar.
     *
     * @param sellCount beklenen satış adedi
     * @return satıştan sonra açılacak slot sayısı
     */
    public synchronized int buySlotsAfter(int sellCount) {
        return portfolio.buySlotsAfter(sellCount);
    }

    /**
     * Yatırım risk profilini döndürür.
     *
     * @return çözümlenmiş risk profili
     */
    public synchronized RiskProfile riskProfile() {
        return settings.riskProfile();
    }

    /**
     * Model türünü döndürür.
     *
     * @return çözümlenmiş model türü
     */
    public synchronized ModelType modelType() {
        return settings.modelType();
    }

    /**
     * Analiz tipini (metrik kümesi) döndürür.
     *
     * @return çözümlenmiş analiz tipi
     */
    public synchronized AnalysisType analysisType() {
        return settings.analysisType();
    }

    /**
     * Seçili endeks adını döndürür.
     *
     * @return endeks adı
     */
    public synchronized String selectedIndex() {
        return settings.selectedIndex();
    }

    /**
     * Son analiz tarihini günceller ve diske yazar.
     *
     * @param date yeni son analiz tarihi
     */
    public synchronized void markRun(LocalDate date) {
        settings = settings.withLastRunDate(date);
        save();
    }

    /**
     * Portföyü bütçe ve parametrelerle başlatır ve diske yazar.
     * <p>
     * Null parametreler eskisini korur. Nakit {@code bütçe − yatırılan maliyet}
     * olarak yeniden hesaplanır.
     *
     * @param budget   başlangıç sermayesi (TL)
     * @param risk     risk profili adı/etiketi (null ise korunur)
     * @param model    model anahtarı (null ise korunur)
     * @param analysis analiz tipi anahtarı (null ise korunur)
     * @param positions açılacak pozisyonlar (kopyalanır; null = boş)
     */
    public synchronized void initialize(double budget, String risk, String model,
                                       String analysis, List<Position> positions) {
        settings = settings.withSelection(risk, model, analysis, null);
        portfolio.initialize(budget, positions);
        save();
    }

    /**
     * Portföyü kısmi olarak günceller ve yeni durumu kaydeder.
     * <p>
     * Bütçe farkı nakde eklenir/çıkarılır (mutlak fark 0.01 TL toleransın
     * altındaysa nakde dokunulmaz). Yeni pozisyon listesi verilirse eski yatırılan
     * maliyet ile yenisi arasındaki fark nakde işlenir. Parametrelerin null
     * olanları korunur.
     *
     * @param budget   yeni bütçe (TL; 0/negatif = korunur)
     * @param risk     risk profili adı/etiketi (null ise korunur)
     * @param model    model anahtarı (null ise korunur)
     * @param analysis analiz tipi anahtarı (null ise korunur)
     * @param index    endeks adı (null ise korunur)
     * @param positions yeni pozisyon listesi (null ise korunur)
     */
    public synchronized void update(double budget, String risk, String model,
                                   String analysis, String index, List<Position> positions) {
        settings = settings.withSelection(risk, model, analysis, index);
        portfolio.adjust(budget, positions);
        save();
    }

    /**
     * Tek bir AL/SAT işlemini doğrulayıp uygular; başarıda diske yazar.
     * <p>
     * Kural ve mutasyon detayları için {@link Portfolio#apply(Transaction)}.
     *
     * @param tx uygulanacak işlem
     * @return uygulama sonucu (red nedeniyle birlikte); reddedilen işlemlerde
     *         sonuç günlüğe {@code WARN} olarak yazılır
     */
    public synchronized TransactionOutcome apply(Transaction tx) {
        TransactionOutcome outcome = portfolio.apply(tx);
        if (!outcome.applied()) {
            log.warn("applyTransaction: {} (sembol={}, islem={}, lot={}, fiyat={})",
                    outcome.detail(), tx.symbol(), tx.action(), tx.lots(), tx.price());
        } else {
            save();
        }
        return outcome;
    }

    /**
     * Metin alanlarından işlem kurup uygular (arayüz/CLI girdisi için).
     *
     * @param symbol hisse sembolü
     * @param action işlem türü ({@code AL} veya {@code SAT})
     * @param lots   işlem lot sayısı
     * @param price  işlem fiyatı (TL)
     * @return işlem uygulandıysa {@code true}
     */
    public synchronized boolean apply(String symbol, String action, int lots, double price) {
        TransactionAction act = TransactionAction.parse(action).orElse(null);
        return apply(new Transaction(symbol, act, lots, price)).applied();
    }

    /**
     * Portföy kurallarını tarar ve ihlalleri bildirir.
     *
     * @return sorun yoksa {@link ValidationResult#OK}; varsa hatalı sonuç
     */
    public synchronized ValidationResult validate() {
        return PortfolioValidator.invariants(portfolio);
    }

    /**
     * Güncel durumu kalıcı depoya yazar.
     */
    private void save() {
        store.save(snapshot());
    }
}