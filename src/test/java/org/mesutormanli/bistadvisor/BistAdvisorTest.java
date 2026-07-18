package org.mesutormanli.bistadvisor;

import org.mesutormanli.bistadvisor.data.PriceScraper;
import org.mesutormanli.bistadvisor.features.TechnicalFeatures;
import org.mesutormanli.bistadvisor.features.TechnicalFeatures.Bar;
import org.mesutormanli.bistadvisor.model.Labeler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Agsiz dogrulama: fixture seri ve Yahoo JSON ile temel mantik test edilir.
 */
class BistAdvisorTest {

    @Test
    void technicalFeaturesFromSeries() {
        // format: tarih,acilis,yuksek,dusuk,kapanis,hacim
        List<String> csv = new ArrayList<>();
        double p = 100.0;
        for (int i = 0; i < 60; i++) {
            csv.add("2026-01-" + (i + 1) + ",0,0,0," + p + ",1000");
            p += (i % 5 - 2); // dalgali
        }
        List<Bar> bars = TechnicalFeatures.toBars(csv);
        assertEquals(60, bars.size());
        double rsi = TechnicalFeatures.rsi(bars, 14);
        assertTrue(rsi >= 0 && rsi <= 100);
        double vol = TechnicalFeatures.volatility(bars, 20);
        assertTrue(vol >= 0);
    }

    @Test
    void labelerAssignsClasses() {
        List<Bar> bars = new ArrayList<>();
        for (int i = 0; i < 30; i++) bars.add(new Bar("d" + i, 100.0, 1000));
        // 20. indeksten 5 gun sonra fiyat 200 (index 25) -> %100 yukselis -> AL
        bars.set(25, new Bar("d25", 200.0, 1000));
        int lbl = Labeler.labelFor(bars, 5, 20);
        assertEquals(Labeler.BUY, lbl);
    }

    @Test
    void priceScraperParsesYahooFixture() {
        String json = """
                {"chart":{"result":[{"timestamp":[1718640000,1718726400],
                "indicators":{"quote":[{"open":[240.0,238.0],"high":[241.0,239.0],
                "low":[239.0,237.0],"close":[240.5,238.5],"volume":[1200,900]}]}}]}}""";
        PriceScraper scraper = new PriceScraper(new org.mesutormanli.bistadvisor.config.AppConfig());
        List<String> rows = scraper.parse(json);
        assertFalse(rows.isEmpty());
        // 1718640000 -> 2024-06-17 16:00 UTC; Istanbul +3 -> 2024-06-17 19:00
        assertTrue(rows.get(0).contains(",240.5,"));
    }
}
