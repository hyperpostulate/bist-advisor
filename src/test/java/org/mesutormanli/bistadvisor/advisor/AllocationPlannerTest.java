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
 * {@link AllocationPlanner} karar kurallarının birim testleri — önerilen düzeltmelerin
 * regresyon kilitleri:
 * <ul>
 *   <li>SAT işaretli sembol aynı turda AL adayı olamaz</li>
 *   <li>Pozisyon slotu yalnızca yeni pozisyonlar için sayılır</li>
 *   <li>Satışların serbest bıraktığı sermaye alım bütçesine eklenir</li>
 *   <li>Fiyat verisi olmayan pozisyonda sahte fiyat/karar üretilmez</li>
 *   <li>Holding önerileri gerçek skoru taşır</li>
 * </ul>
 * Testler tamamen deterministiktir; ağa çıkmaz.
 */
class AllocationPlannerTest {

    /** %10'luk stop-loss, 0.75 alım eşiği, %25 risk — konservatif senaryolar için. */
    private static final AdvisorMode MODE = AdvisorMode.CONSERVATIVE;

    @Test
    void satIsaretiAyniSemboluAlListesineKoymaz() {
        // ASTOR stop-loss'ta (−%15) → SAT; aynı sembol modelden BUY alsa bile AL'a giremez
        HoldingInput holding = new HoldingInput("ASTOR", 101, 200.0, 170.0, Labeler.BUY, 0.90);
        CandidateInput candidate = new CandidateInput("ASTOR", 170.0, 0.90, Labeler.BUY, true);

        Plan plan = AllocationPlanner.plan(List.of(holding), List.of(candidate),
                MODE, ModelType.RANDOM_FOREST, 10_000, 5);

        assertEquals("SAT", plan.holdings().get(0).action());
        assertTrue(plan.buys().isEmpty(), "SAT işaretli sembol AL adayı olmamalı: " + plan.buys());
    }

    @Test
    void mevcutPozisyonaEklemeSlotHarcamazVeYeniAdayDisaridaKalir() {
        // portföy dolu (5 pozisyon), satış yok → yeni pozisyon slotu 0
        HoldingInput h1 = new HoldingInput("AAA", 10, 100.0, 110.0, Labeler.HOLD, 0.60);
        HoldingInput h2 = new HoldingInput("BBB", 10, 100.0, 110.0, Labeler.HOLD, 0.60);
        HoldingInput h3 = new HoldingInput("CCC", 10, 100.0, 110.0, Labeler.HOLD, 0.60);
        HoldingInput h4 = new HoldingInput("DDD", 10, 100.0, 110.0, Labeler.HOLD, 0.60);
        HoldingInput h5 = new HoldingInput("EEE", 10, 100.0, 110.0, Labeler.HOLD, 0.60);
        // mevcut pozisyona ekleme (AAA) ve yeni pozisyon adayı (ZZZ, daha yüksek skorlu)
        CandidateInput addExisting = new CandidateInput("AAA", 110.0, 0.90, Labeler.BUY, true);
        CandidateInput brandNew = new CandidateInput("ZZZ", 50.0, 0.95, Labeler.BUY, false);

        Plan plan = AllocationPlanner.plan(List.of(h1, h2, h3, h4, h5),
                List.of(addExisting, brandNew), MODE, ModelType.RANDOM_FOREST, 50_000, 5);

        assertEquals(0, plan.buySlots());
        assertEquals(1, plan.buys().size(), "slot yokken yalnız mevcut pozisyona ekleme önerilmeli");
        assertEquals("AAA", plan.buys().get(0).symbol());
    }

    @Test
    void acilanSlotYeniPozisyonaKullanilir() {
        // 5 pozisyon, 1 SAT → 1 slot açılır; hem ekleme hem yeni aday seçilebilir
        HoldingInput sold = new HoldingInput("AAA", 10, 100.0, 80.0, Labeler.HOLD, 0.60);   // −%20 → SAT
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

    @Test
    void satislarinSerbestBiraktigiSermayeAlimButcesineEklenir() {
        // nakit 0; tek satış 100 lot @ 200 → 20.000 serbest kalır
        HoldingInput sold = new HoldingInput("THYAO", 100, 240.0, 200.0, Labeler.HOLD, 0.60); // −%16,7 → SAT
        CandidateInput buy = new CandidateInput("AKBNK", 10.0, 0.90, Labeler.BUY, false);

        Plan plan = AllocationPlanner.plan(List.of(sold), List.of(buy),
                MODE, ModelType.RANDOM_FOREST, 0, 5);

        assertEquals(20_000, plan.plannedCapital(), 0.01);
        assertEquals(1, plan.buys().size());
        // risk %25 → 5.000 bütçe, 10 TL'lik hisseden 500 lot
        assertEquals(500, plan.buys().get(0).lots());
    }

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

    @Test
    void esikAltiAlimAdayiEle() {
        CandidateInput below = new CandidateInput("AKBNK", 10.0, 0.50, Labeler.BUY, false);  // < 0.75
        CandidateInput above = new CandidateInput("GARAN", 10.0, 0.80, Labeler.BUY, false);

        Plan plan = AllocationPlanner.plan(List.of(), List.of(below, above),
                MODE, ModelType.RANDOM_FOREST, 50_000, 5);

        assertEquals(1, plan.buys().size());
        assertEquals("GARAN", plan.buys().get(0).symbol());
    }

    @Test
    void modelSkoruEsigiGecmeyenPozisyonSatilmaz() {
        // −%5 zarar stop-loss'u tetiklemez (eşik %10); SAT skoru 0.20 < 0.30 → TUT
        HoldingInput holding = new HoldingInput("THYAO", 100, 240.0, 228.0, Labeler.SELL, 0.20);

        Plan plan = AllocationPlanner.plan(List.of(holding), List.of(),
                MODE, ModelType.RANDOM_FOREST, 50_000, 5);

        assertEquals("TUT", plan.holdings().get(0).action());
    }

    @Test
    void modelSkoruEsigiGecenPozisyonSatilir() {
        HoldingInput holding = new HoldingInput("THYAO", 100, 240.0, 230.0, Labeler.SELL, 0.50);

        Plan plan = AllocationPlanner.plan(List.of(holding), List.of(),
                MODE, ModelType.RANDOM_FOREST, 50_000, 5);

        assertEquals("SAT", plan.holdings().get(0).action());
    }
}