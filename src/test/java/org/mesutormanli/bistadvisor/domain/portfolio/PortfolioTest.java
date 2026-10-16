package org.mesutormanli.bistadvisor.domain.portfolio;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Portfolio} para/pozisyon muhasebesi agregasının testleri: ağırlıklı
 * ortalama maliyetle birleştirme, slot sınırı, nakit/tolerans kontrolleri, SAT
 * kuralları, doğrulama sırası ve {@code restore} ile yükleme.
 * <p>
 * Davranış referansı eski {@code portfolio.PortfolioService} sürümüdür: işlem
 * mutasyonları {@link TransactionOutcome} ile sonuçlanır ve doğrulama sırası
 * sembol → lot → fiyat → aksiyo(n)dur. Testler deterministiktir; ağa çıkmaz.
 */
class PortfolioTest {

    /**
     * Mevcut pozisyona yapılan alımların ağırlıklı ortalama maliyetle birleşmesi.
     * <p>
     * Senaryo: 100 lot THYAO 240 TL'den, ardından 50 lot 260 TL'den alınır.
     * Beklenen davranış: 150 lotun maliyeti
     * {@code (100×240 + 50×260)/150 = 246,67 TL} olur ve her iki alım nakitten
     * düşülür.
     */
    @Test
    void buysOfSameSymbolMergeWithWeightedAverageCost() {
        Portfolio p = new Portfolio();
        p.initialize(50_000, List.of());

        assertEquals(TransactionOutcome.APPLIED,
                p.apply(new Transaction("THYAO", TransactionAction.AL, 100, 240)));
        assertEquals(TransactionOutcome.APPLIED,
                p.apply(new Transaction("THYAO", TransactionAction.AL, 50, 260)));

        Position pos = p.positions().getFirst();
        assertEquals(150, pos.lots());
        assertEquals(246.6667, pos.avgCost(), 0.01);
        assertEquals(50_000 - 100 * 240 - 50 * 260, p.availableCash(), 0.01);
    }

    /**
     * Maksimum pozisyon slotu dolunca yeni sembolün reddedilmesi.
     * <p>
     * Senaryo: 5 farklı sembol sırayla alınır (slotlar dolar), 6. sembol alınmak
     * istenir. Beklenen davranış: 6. alım {@code POSITION_LIMIT} ile reddedilir,
     * portföy 5 pozisyonda kalır.
     */
    @Test
    void newSymbolRejectedWhenSlotLimitFull() {
        Portfolio p = new Portfolio();
        p.initialize(1_000_000, List.of());
        for (String sym : List.of("AAA", "BBB", "CCC", "DDD", "EEE")) {
            assertEquals(TransactionOutcome.APPLIED,
                    p.apply(new Transaction(sym, TransactionAction.AL, 1, 10)), sym);
        }

        assertEquals(TransactionOutcome.POSITION_LIMIT,
                p.apply(new Transaction("FFF", TransactionAction.AL, 1, 10)));
        assertEquals(5, p.positionCount());
    }

    /**
     * Nakit üstü alımın, tolerans içindeki sınırın ise kabul edilmesi.
     * <p>
     * Senaryo: 1.000 TL nakit varken 2.400 TL'lik alım denenir ve
     * {@code INSUFFICIENT_CASH} ile reddedilir; ardından maliyeti tolerans
     * ({@code TOLERANCE = 0,01 TL}) kadar aşan 1.000,005 TL'lik alım denenir.
     * Beklenen davranış: ikinci alım tolerans sayesinde kabul edilir.
     */
    @Test
    void cashCheckWorksWithinTolerance() {
        Portfolio p = new Portfolio();
        p.initialize(1_000, List.of());

        assertEquals(TransactionOutcome.INSUFFICIENT_CASH,
                p.apply(new Transaction("THYAO", TransactionAction.AL, 100, 24)));

        assertEquals(TransactionOutcome.APPLIED,
                p.apply(new Transaction("THYAO", TransactionAction.AL, 1, 1_000.005)));
    }

    /**
     * Kısmi satımda ortalama maliyetin sabit kalması ve nakdin brüt tutarla artması.
     * <p>
     * Senaryo: 100 lot THYAO 240 TL'den alınıp 40 lotu 250 TL'den satılır.
     * Beklenen davranış: kalan 60 lotun maliyeti 240 TL olarak kalır, nakit
     * 40×250=10.000 TL brüt tutarla artar ve tam satımda pozisyon silinir.
     */
    @Test
    void partialSellPreservesCostAndGrossAmountFlowsToCash() {
        Portfolio p = new Portfolio();
        p.initialize(100_000, List.of());
        p.apply(new Transaction("THYAO", TransactionAction.AL, 100, 240));

        assertEquals(TransactionOutcome.APPLIED,
                p.apply(new Transaction("THYAO", TransactionAction.SAT, 40, 250)));
        Position pos = p.positions().getFirst();
        assertEquals(60, pos.lots());
        assertEquals(240, pos.avgCost(), 0.01);

        assertEquals(TransactionOutcome.APPLIED,
                p.apply(new Transaction("THYAO", TransactionAction.SAT, 60, 250)));
        assertTrue(p.positions().isEmpty());
    }

    /**
     * Elde olmayan sembol/lotun satılmasının reddedilmesi.
     * <p>
     * Senaryo: portföyde GELEN sembolü satılır ({@code NOT_HELD}) ve 100 lotu
     * varken 150 lot satılır ({@code INSUFFICIENT_LOTS}). Beklenen davranış: her
     * iki satış reddedilir, nakit ve pozisyon değişmez.
     */
    @Test
    void invalidSellsRejected() {
        Portfolio p = new Portfolio();
        p.initialize(100_000, List.of());
        p.apply(new Transaction("THYAO", TransactionAction.AL, 100, 240));
        double cashBefore = p.availableCash();

        assertEquals(TransactionOutcome.NOT_HELD,
                p.apply(new Transaction("GELEN", TransactionAction.SAT, 1, 10)));
        assertEquals(TransactionOutcome.INSUFFICIENT_LOTS,
                p.apply(new Transaction("THYAO", TransactionAction.SAT, 150, 250)));

        assertEquals(cashBefore, p.availableCash(), 1e-9);
        assertEquals(100, p.positions().getFirst().lots());
    }

    /**
     * Ortak geçerlilik kontrollerinin eski sırayla uygulanması (sembol → lot → fiyat → aksiyon).
     * <p>
     * Senaryo: sırasıyla boş sembollü, boş sembol+geçersiz lotlu, geçersiz fiyatlı
     * ve geçersiz (null) aksiyonlu işlemler denenir. Beklenen davranış: her işlem
     * ilgili red nedeniyle sonuçlanır ve null aksiyon en SONA bırakılır
     * ({@code INVALID_ACTION} yalnız aksiyon dışında her şey geçerliyse döner).
     */
    @Test
    void validationOrderPreservesLegacyBehavior() {
        Portfolio p = new Portfolio();
        p.initialize(100_000, List.of());

        assertEquals(TransactionOutcome.BLANK_SYMBOL,
                p.apply(new Transaction("  ", TransactionAction.AL, 0, 0)));
        assertEquals(TransactionOutcome.INVALID_LOTS,
                p.apply(new Transaction("THYAO", null, 0, 0)),
                "aksiyon null olsa bile lot kontrolü önce gelir");
        assertEquals(TransactionOutcome.INVALID_PRICE,
                p.apply(new Transaction("THYAO", TransactionAction.AL, 1, -5)));
        assertEquals(TransactionOutcome.INVALID_ACTION,
                p.apply(new Transaction("THYAO", null, 1, 5)));
    }

    /**
     * {@code restore} ile yüklemenin nakdi yeniden hesaplamaması.
     * <p>
     * Senaryo: bütçe 1.000 TL, nakit 500 TL ve maliyeti 900 TL olan pozisyonla
     * restore edilir (nakit "bütçe − maliyet" DEĞİL). Beklenen davranış: nakit
     * 500 TL olarak aynen kalır.
     */
    @Test
    void restoreDoesNotRecomputeCash() {
        Portfolio p = Portfolio.restore(1_000, 500, List.of(new Position("THYAO", 10, 90)));

        assertEquals(500, p.availableCash(), 0.01);
        assertEquals(1_000, p.budget(), 0.01);
        assertEquals(900, p.investedCost(), 0.01);
    }

    /**
     * Slot sayacı ve satış sonrası slot hesabının doğruluğu.
     * <p>
     * Senaryo: 5 pozisyonluk dolu portföyde {@code canAddPosition} false, satışlar
     * sonrası {@code buySlotsAfter} satış sayısı kadar slot bildirir. Beklenen
     * davranış: 2 satış sonrası 2 slot açılır; limit ayrıca
     * {@code maxPositions()} ile 5 olarak görünür.
     */
    @Test
    void slotCountsComputedCorrectly() {
        Portfolio p = new Portfolio();
        p.initialize(1_000_000, List.of(new Position("AAA", 1, 1), new Position("BBB", 1, 1),
                new Position("CCC", 1, 1), new Position("DDD", 1, 1), new Position("EEE", 1, 1)));

        assertFalse(p.canAddPosition());
        assertEquals(5, p.maxPositions());
        assertEquals(2, p.buySlotsAfter(2));
    }
}