package org.mesutormanli.bistadvisor.advisor;

import org.junit.jupiter.api.Test;
import org.mesutormanli.bistadvisor.advisor.AllocationPlanner.CandidateInput;
import org.mesutormanli.bistadvisor.advisor.AllocationPlanner.HoldingInput;
import org.mesutormanli.bistadvisor.advisor.AllocationPlanner.Plan;
import org.mesutormanli.bistadvisor.advisor.AllocationPlanner.Planned;
import org.mesutormanli.bistadvisor.config.AdvisorMode;
import org.mesutormanli.bistadvisor.config.ModelType;
import org.mesutormanli.bistadvisor.model.Labeler;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AllocationPlanner#plan} karar kurallarının senaryo testleri: SAT/AL
 * çakışmasının engellenmesi, pozisyon slot yönetimi, satış gelirinin bütçeye
 * katılması, holding notlarının ve eşiğin altındaki adayların elenmesi.
 * <p>
 * Bütün senaryolar {@code MODE = AdvisorMode.CONSERVATIVE} sabitiyle koşturulur:
 * bu mod %25 risk payı ({@code riskPct}), 0,75 alım eşiği ({@code buyThreshold}),
 * %10 stop-loss ({@code stopLossPct}) ve 0,30 SAT skor eşiği
 * ({@code sellScoreThreshold}) değerlerini kullanır. Model tipi her senaryoda
 * {@link ModelType#RANDOM_FOREST} olduğundan {@link ScoreGate} taban eşikleri
 * aynen uygular. Testler deterministiktir; ağa çıkmaz.
 */
class AllocationPlannerTest {

    private static final AdvisorMode MODE = AdvisorMode.CONSERVATIVE;

    /**
     * Stop-loss'a düşen sembolün aynı turda yeniden satın alınmaması.
     * <p>
     * Senaryo: 200 TL maliyetli ASTOR pozisyonu 170 TL'ye (%15 zarar) düşer ve
     * %10 stop-loss eşiğini geçerek SAT işaretlenir; model aynı sembol için
     * BUY/0.90 alım adayı üretir. Beklenen davranış: holding SAT ile sonuçlanır ve
     * sembol aynı turda satıldığı için AL listesine girmez ({@code plan.buys()}
     * boş kalır).
     */
    @Test
    void satIsaretiAyniSemboluAlListesineKoymaz() {
        HoldingInput holding = new HoldingInput("ASTOR", 101, 200.0, 170.0, Labeler.BUY, 0.90);
        CandidateInput candidate = new CandidateInput("ASTOR", 170.0, 0.90, Labeler.BUY, true);

        Plan plan = AllocationPlanner.plan(List.of(holding), List.of(candidate),
                MODE, ModelType.RANDOM_FOREST, 10_000, 5);

        assertEquals("SAT", plan.holdings().get(0).action());
        assertTrue(plan.buys().isEmpty(), "SAT işaretli sembol AL adayı olmamalı: " + plan.buys());
    }

    /**
     * Portföy doluyken yalnız mevcut pozisyona ekleme yapılması.
     * <p>
     * Senaryo: 5 pozisyonun tamamı TUT'tadır (portföy dolu, satış yok); AAA için
     * 0.90 skorlu ekleme adayı, ZZZ için 0.95 skorlu yeni aday sunulur. Beklenen
     * davranış: mevcut pozisyona ekleme slot harcamaz ({@code buySlots} 0 kalır),
     * yeni aday ZZZ dışarıda kalır ve yalnız AAA için 1 adet AL önerisi yapılır.
     */
    @Test
    void mevcutPozisyonaEklemeSlotHarcamazVeYeniAdayDisaridaKalir() {
        HoldingInput h1 = new HoldingInput("AAA", 10, 100.0, 110.0, Labeler.HOLD, 0.60);
        HoldingInput h2 = new HoldingInput("BBB", 10, 100.0, 110.0, Labeler.HOLD, 0.60);
        HoldingInput h3 = new HoldingInput("CCC", 10, 100.0, 110.0, Labeler.HOLD, 0.60);
        HoldingInput h4 = new HoldingInput("DDD", 10, 100.0, 110.0, Labeler.HOLD, 0.60);
        HoldingInput h5 = new HoldingInput("EEE", 10, 100.0, 110.0, Labeler.HOLD, 0.60);
        CandidateInput addExisting = new CandidateInput("AAA", 110.0, 0.90, Labeler.BUY, true);
        CandidateInput brandNew = new CandidateInput("ZZZ", 50.0, 0.95, Labeler.BUY, false);

        Plan plan = AllocationPlanner.plan(List.of(h1, h2, h3, h4, h5),
                List.of(addExisting, brandNew), MODE, ModelType.RANDOM_FOREST, 50_000, 5);

        assertEquals(0, plan.buySlots());
        assertEquals(1, plan.buys().size(), "slot yokken yalnız mevcut pozisyona ekleme önerilmeli");
        assertEquals("AAA", plan.buys().get(0).symbol());
    }

    /**
     * Satışın açtığı slotun yeni pozisyona harcanması.
     * <p>
     * Senaryo: AAA pozisyonu 100 TL maliyetle 80 TL'ye (%20 zarar) düşerek
     * stop-loss ile SAT'a gider ve 5 kişilik portföyde bir slot boşalır; BBB için
     * 0.80 skorlu ekleme, ZZZ için 0.90 skorlu yeni aday vardır. Beklenen davranış:
     * {@code buySlots} 1 olur ve hem mevcut BBB'ye ekleme hem de yeni ZZZ için AL
     * önerisi üretilir (toplam 2 AL).
     */
    @Test
    void acilanSlotYeniPozisyonaKullanilir() {
        HoldingInput sold = new HoldingInput("AAA", 10, 100.0, 80.0, Labeler.HOLD, 0.60);
        HoldingInput h2 = new HoldingInput("BBB", 10, 100.0, 110.0, Labeler.HOLD, 0.60);
        HoldingInput h3 = new HoldingInput("CCC", 10, 100.0, 110.0, Labeler.HOLD, 0.60);
        HoldingInput h4 = new HoldingInput("DDD", 10, 100.0, 110.0, Labeler.HOLD, 0.60);
        HoldingInput h5 = new HoldingInput("EEE", 10, 100.0, 110.0, Labeler.HOLD, 0.60);
        CandidateInput addExisting = new CandidateInput("BBB", 110.0, 0.80, Labeler.BUY, true);
        CandidateInput brandNew = new CandidateInput("ZZZ", 50.0, 0.90, Labeler.BUY, false);

        Plan plan = AllocationPlanner.plan(List.of(sold, h2, h3, h4, h5),
                List.of(addExisting, brandNew), MODE, ModelType.RANDOM_FOREST, 50_000, 5);

        assertEquals(1, plan.buySlots());
        assertEquals(2, plan.buys().size());
        assertTrue(plan.buys().stream().anyMatch(b -> b.symbol().equals("ZZZ")));
        assertTrue(plan.buys().stream().anyMatch(b -> b.symbol().equals("BBB")));
    }

    /**
     * Satış gelirinin alım bütçesine eklenmesi.
     * <p>
     * Senaryo: 240 TL maliyetli 100 lot THYAO, 200 TL'ye (%15 zarar) düşerek
     * stop-loss ile SAT'a gider; bu satış 20.000 TL serbest bırakır, nakit 0 TL'dir.
     * Beklenen davranış: planlanan sermaye 20.000 TL olur, %25 risk payıyla
     * ayrılan 5.000 TL bütçe tek aday AKBNK'ye (10 TL) gider ve 500 lot AL önerilir.
     */
    @Test
    void satislarinSerbestBiraktigiSermayeAlimButcesineEklenir() {
        HoldingInput sold = new HoldingInput("THYAO", 100, 240.0, 200.0, Labeler.HOLD, 0.60);
        CandidateInput buy = new CandidateInput("AKBNK", 10.0, 0.90, Labeler.BUY, false);

        Plan plan = AllocationPlanner.plan(List.of(sold), List.of(buy),
                MODE, ModelType.RANDOM_FOREST, 0, 5);

        assertEquals(20_000, plan.plannedCapital(), 0.01);
        assertEquals(1, plan.buys().size());
        assertEquals(500, plan.buys().get(0).lots());
    }

    /**
     * TUT kararında holding skorunun ve kâr yüzdesinin nota işlenmesi.
     * <p>
     * Senaryo: 240 TL maliyetli THYAO 250 TL'dedir (+%4,17 kâr, stop-loss yok) ve
     * model skoru 0.42'dir. Beklenen davranış: TUT kararı verilir,
     * {@link Planned#score} 0.42 olarak aynen taşınır ve {@link Planned#note}
     * {@code +4.17%} yüzdesiyle başlar.
     */
    @Test
    void holdingSkoruDoldurulurVeNotYuzdeyiTasir() {
        HoldingInput holding = new HoldingInput("THYAO", 100, 240.0, 250.0, Labeler.HOLD, 0.42);

        Plan plan = AllocationPlanner.plan(List.of(holding), List.of(),
                MODE, ModelType.RANDOM_FOREST, 50_000, 5);

        Planned p = plan.holdings().get(0);
        assertEquals("TUT", p.action());
        assertEquals(0.42, p.score(), 1e-9);
        assertTrue(p.note().startsWith("+4.17%"), "not yüzden içermeli: " + p.note());
    }

    /**
     * Fiyat verisi olmayan pozisyonda sahte fiyat/skor üretilmemesi ve uyarı verilmesi.
     * <p>
     * Senaryo: fiyatı, sınıfı ve skoru bilinmeyen (null) bir BOZUK pozisyonu
     * sunulur. Beklenen davranış: temkinli TUT kararı çıkar; ancak
     * {@link Planned#price} ve {@link Planned#score} null kalır (sahte değer
     * uydurulmaz) ve {@code plan.warnings()} tam 1 uyarı içerir.
     */
    @Test
    void fiyatVerisiOlmayanPozisyondaSahteFiyatUretilmez() {
        HoldingInput holding = new HoldingInput("BOZUK", 100, 240.0, null, null, null);

        Plan plan = AllocationPlanner.plan(List.of(holding), List.of(),
                MODE, ModelType.RANDOM_FOREST, 50_000, 5);

        Planned p = plan.holdings().get(0);
        assertEquals("TUT", p.action());
        assertNull(p.price(), "sahte fiyat üretilmemeli");
        assertNull(p.score());
        assertEquals(1, plan.warnings().size());
    }

    /**
     * Alım eşiğinin altındaki adayın elenmesi.
     * <p>
     * Senaryo: AKBNK 0,50 skorla, GARAN 0,80 skorla BUY adayıdır; CONSERVATIVE
     * alım eşiği 0,75'tir. Beklenen davranış: 0,50'lik aday elenir ve AL listesinde
     * yalnız GARAN kalır (1 adet AL önerisi).
     */
    @Test
    void esikAltiAlimAdayiEle() {
        CandidateInput below = new CandidateInput("AKBNK", 10.0, 0.50, Labeler.BUY, false);
        CandidateInput above = new CandidateInput("GARAN", 10.0, 0.80, Labeler.BUY, false);

        Plan plan = AllocationPlanner.plan(List.of(), List.of(below, above),
                MODE, ModelType.RANDOM_FOREST, 50_000, 5);

        assertEquals(1, plan.buys().size());
        assertEquals("GARAN", plan.buys().get(0).symbol());
    }

    /**
     * SAT skoru eşiği geçmeyen pozisyonun satılmaması.
     * <p>
     * Senaryo: 240 TL maliyetli THYAO 228 TL'dedir (%5 zarar, %10 stop-loss'un
     * altında); model sınıfı SELL olsa da skoru 0,20'dir ve 0,30 SAT skor eşiğini
     * geçemez ({@code 0.20 < 0.30}). Beklenen davranış: pozisyon satılmaz, TUT
     * önerilir.
     */
    @Test
    void modelSkoruEsigiGecmeyenPozisyonSatilmaz() {
        HoldingInput holding = new HoldingInput("THYAO", 100, 240.0, 228.0, Labeler.SELL, 0.20);

        Plan plan = AllocationPlanner.plan(List.of(holding), List.of(),
                MODE, ModelType.RANDOM_FOREST, 50_000, 5);

        assertEquals("TUT", plan.holdings().get(0).action());
    }

    /**
     * SAT skoru eşiğini geçen pozisyonun satılması.
     * <p>
     * Senaryo: 240 TL maliyetli THYAO 230 TL'dedir (stop-loss yok) ve model sınıfı
     * SELL, skoru 0,50'dir ({@code 0.50 >= 0.30}). Beklenen davranış: model sinyali
     * eşiği geçtiği için pozisyon SAT olarak işaretlenir.
     */
    @Test
    void modelSkoruEsigiGecenPozisyonSatilir() {
        HoldingInput holding = new HoldingInput("THYAO", 100, 240.0, 230.0, Labeler.SELL, 0.50);

        Plan plan = AllocationPlanner.plan(List.of(holding), List.of(),
                MODE, ModelType.RANDOM_FOREST, 50_000, 5);

        assertEquals("SAT", plan.holdings().get(0).action());
    }
}