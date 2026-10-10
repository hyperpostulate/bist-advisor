package org.mesutormanli.bistadvisor.domain.portfolio;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Portföyün para ve pozisyon mutasyonlarından sorumlu alan nesnesi (aggregate).
 * <p>
 * Tek sorumluluğu portföy muhasebesidir: bütçe/nakit takibi, pozisyon slotları,
 * ağırlıklı ortalama maliyetle alım ve satışın nakde yansıması. Disk erişimi,
 * doğrulama kuralları ve ayarlar başka bileşenlerdedir; bu sınıf I/O yapmaz ve
 * bu yüzden ağ/disk olmadan birim test edilebilir. Erişim metodları
 * {@code synchronized} olduğundan örnek thread-safe'tir. Pozisyonlar sembole
 * göre alfabetik sıralı tutulur; dışarıya kopya döndürülür.
 */
public final class Portfolio {
    private double budget;
    private double cash;
    private final List<Position> positions = new ArrayList<>();

    /**
     * Boş portföy kurar (bütçe ve nakit 0).
     */
    public Portfolio() {
    }

    /**
     * Kayıtlı durumu birebir geri yükler: nakit yeniden hesaplanmaz, olduğu
     * gibi alınır.
     *
     * @param budget    sermaye (TL)
     * @param cash      serbest nakit (TL)
     * @param positions pozisyonlar (kopyalanır ve sıralanır)
     * @return geri yüklenmiş portföy
     */
    public static Portfolio restore(double budget, double cash, Collection<Position> positions) {
        Portfolio p = new Portfolio();
        p.budget = budget;
        p.cash = cash;
        if (positions != null) {
            for (Position pos : positions) {
                if (pos != null) p.positions.add(pos);
            }
        }
        p.sort();
        return p;
    }

    /**
     * Bütçeyi döndürür.
     *
     * @return sermaye (TL)
     */
    public synchronized double budget() {
        return budget;
    }

    /**
     * Serbest nakdi döndürür.
     *
     * @return serbest nakit (TL)
     */
    public synchronized double availableCash() {
        return cash;
    }

    /**
     * Pozisyonlara yatırılan toplam maliyeti hesaplar.
     *
     * @return yatırılan toplam maliyet (Σ lot × ortalama maliyet, TL)
     */
    public synchronized double investedCost() {
        double total = 0.0;
        for (Position p : positions) {
            total += p.lots() * p.avgCost();
        }
        return total;
    }

    /**
     * Pozisyonların sembole göre sıralı kopyasını döndürür.
     *
     * @return pozisyon listesi kopyası
     */
    public synchronized List<Position> positions() {
        return List.copyOf(positions);
    }

    /**
     * Pozisyon sayısını döndürür.
     *
     * @return açık pozisyon adedi
     */
    public synchronized int positionCount() {
        return positions.size();
    }

    /**
     * Yeni pozisyon eklenebilirliğini belirler.
     *
     * @return slot sınırı dolu değilse {@code true}
     */
    public synchronized boolean canAddPosition() {
        return positions.size() < PortfolioPolicy.MAX_POSITIONS;
    }

    /**
     * Taşınabilecek en fazla pozisyon sayısını döndürür.
     *
     * @return {@link PortfolioPolicy#MAX_POSITIONS}
     */
    public synchronized int maxPositions() {
        return PortfolioPolicy.MAX_POSITIONS;
    }

    /**
     * Satışlardan sonra açılacak boş slot sayısını hesaplar.
     *
     * @param sellCount beklenen satış adedi
     * @return satıştan sonra açılacak slot sayısı (0 ile sınır arasında kırpılır)
     */
    public synchronized int buySlotsAfter(int sellCount) {
        return Math.clamp(PortfolioPolicy.MAX_POSITIONS - (positions.size() - sellCount),
                0, PortfolioPolicy.MAX_POSITIONS);
    }

    /**
     * Portföyü bütçe ve pozisyonlarla başlatır.
     * <p>
     * Nakit, {@code bütçe − yatırılan maliyet} olarak yeniden hesaplanır.
     *
     * @param newBudget başlangıç sermayesi (TL)
     * @param newPos    açılacak pozisyonlar (kopyalanır; {@code null} = boş)
     */
    public synchronized void initialize(double newBudget, List<Position> newPos) {
        budget = newBudget;
        positions.clear();
        if (newPos != null) {
            for (Position p : newPos) {
                if (p != null) positions.add(p);
            }
        }
        sort();
        cash = budget - investedCost();
    }

    /**
     * Portföyü kısmi olarak günceller.
     * <p>
     * Bütçe farkı nakde eklenir/çıkarılır (mutlak fark toleransın altındaysa nakde
     * dokunulmaz; sıfır/negatif bütçe verilmez sayılır). Yeni pozisyon listesi
     * verilirse eski yatırılan maliyet ile yenisi arasındaki fark nakde işlenir:
     * pozisyon eklemek nakdi maliyeti kadar azaltır, çıkarmak artırır.
     *
     * @param newBudget yeni bütçe (TL; 0/negatif = korunur)
     * @param newPos    yeni pozisyon listesi ({@code null} = korunur)
     */
    public synchronized void adjust(double newBudget, List<Position> newPos) {
        if (newBudget > 0 && Math.abs(newBudget - budget) > PortfolioPolicy.TOLERANCE) {
            cash += newBudget - budget;
            budget = newBudget;
        }
        if (newPos != null) {
            double oldInvested = investedCost();
            positions.clear();
            for (Position p : newPos) {
                if (p != null) positions.add(p);
            }
            sort();
            cash += oldInvested - investedCost();
        }
    }

    /**
     * Tek bir AL/SAT işlemini doğrulayıp uygular.
     * <p>
     * Ortak geçerlilik kontrolleri: sembol boş olamaz, lot &gt; 0, fiyat sonlu ve
     * &gt; 0, işlem türü tanımlı olmalı. <strong>AL</strong> — maliyet (lot × fiyat)
     * nakdi (toleransla) aşarsa reddedilir. Mevcut pozisyona alım AĞIRLIKLI ORTALAMA
     * MALİYETLE birleştirilir. Yeni sembolde slot sınırı uygulanır; maliyet nakitten
     * düşülür. <strong>SAT</strong> — pozisyon yoksa veya lot yetmezse reddedilir.
     * Tam satımda pozisyon silinir; kısmi satımda lot azalır ve ortalama maliyet
     * DEĞİŞMEZ. Brüt tutar (lot × fiyat) nakde eklenir; böylece gerçekleşen
     * kâr/zarar nakde yansır.
     *
     * @param tx uygulanacak işlem
     * @return uygulama sonucu (red nedeniyle birlikte)
     */
    public synchronized TransactionOutcome apply(Transaction tx) {
        if (tx.symbol() == null || tx.symbol().isBlank()) return TransactionOutcome.BLANK_SYMBOL;
        if (tx.lots() <= 0) return TransactionOutcome.INVALID_LOTS;
        if (tx.price() <= 0 || !Double.isFinite(tx.price())) return TransactionOutcome.INVALID_PRICE;
        if (tx.action() == null) return TransactionOutcome.INVALID_ACTION;

        String sym = tx.symbol().trim().toUpperCase();
        if (tx.action() == TransactionAction.AL) {
            double cost = tx.lots() * tx.price();
            if (cost > cash + PortfolioPolicy.TOLERANCE) return TransactionOutcome.INSUFFICIENT_CASH;
            int idx = indexOf(sym);
            if (idx >= 0) {
                Position old = positions.get(idx);
                int totalLots = old.lots() + tx.lots();
                double newAvgCost = (old.avgCost() * old.lots() + tx.price() * tx.lots()) / totalLots;
                positions.set(idx, new Position(sym, totalLots, newAvgCost));
            } else {
                if (!canAddPosition()) return TransactionOutcome.POSITION_LIMIT;
                positions.add(new Position(sym, tx.lots(), tx.price()));
            }
            cash -= cost;
            sort();
            return TransactionOutcome.APPLIED;
        }

        int idx = indexOf(sym);
        if (idx < 0) return TransactionOutcome.NOT_HELD;
        Position old = positions.get(idx);
        if (tx.lots() > old.lots()) return TransactionOutcome.INSUFFICIENT_LOTS;
        if (tx.lots() == old.lots()) {
            positions.remove(idx);
        } else {
            positions.set(idx, new Position(sym, old.lots() - tx.lots(), old.avgCost()));
        }
        cash += tx.lots() * tx.price();
        sort();
        return TransactionOutcome.APPLIED;
    }

    private int indexOf(String symbol) {
        for (int i = 0; i < positions.size(); i++) {
            if (positions.get(i).symbol().equals(symbol)) return i;
        }
        return -1;
    }

    private void sort() {
        positions.sort(Comparator.comparing(Position::symbol));
    }
}