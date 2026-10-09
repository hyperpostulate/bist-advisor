package org.mesutormanli.bistadvisor;

import org.mesutormanli.bistadvisor.data.YahooClient;
import org.mesutormanli.bistadvisor.features.TechnicalFeatures;
import org.mesutormanli.bistadvisor.features.TechnicalFeatures.Bar;
import org.mesutormanli.bistadvisor.model.Labeler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Üretim yardımcılarının uçtan uca birim testleri: teknik gösterge hesaplama,
 * makine öğrenmesi etiketleme ve Yahoo Finance JSON ayrıştırma.
 * <p>
 * Tüm seriler dosya içinde sentetik olarak üretilir; hiçbir test ağa çıkmaz ve
 * gerçek piyasa verisine bağımlı değildir.
 */
class BistAdvisorTest {

    /**
     * Sentetik OHLCV serisinden bar üretimi ve göstergelerin sınanması.
     * <p>
     * 100 TL'den başlayıp {@code (i % 5 - 2)} adımlarıyla hafif salınan 60 günlük
     * kapanış serisi {@code tarih,0,0,0,kapanış,hacim} biçiminde CSV satırlarına
     * çevrilir. Beklenen davranış: {@link TechnicalFeatures#toBars} 60 {@link Bar}
     * üretir, 14 günlük {@link TechnicalFeatures#rsi} 0 ile 100 arasında kalır ve
     * 20 günlük {@link TechnicalFeatures#volatility} negatif olmaz.
     */
    @Test
    void technicalFeaturesFromSeries() {
        List<String> csv = new ArrayList<>();
        double p = 100.0;
        for (int i = 0; i < 60; i++) {
            csv.add("2026-01-" + (i + 1) + ",0,0,0," + p + ",1000");
            p += (i % 5 - 2);
        }
        List<Bar> bars = TechnicalFeatures.toBars(csv);
        assertEquals(60, bars.size());
        double rsi = TechnicalFeatures.rsi(bars, 14);
        assertTrue(rsi >= 0 && rsi <= 100);
        double vol = TechnicalFeatures.volatility(bars, 20);
        assertTrue(vol >= 0);
    }

    /**
     * İleriye dönük fiyat sıçramasında etiketleyicinin AL sınıfını üretmesi.
     * <p>
     * 30 barlık düz 100 TL seride 25. indeksteki bar 200 TL'ye yükselir; örnek
     * indeksi 20'den 5 bar ileriye bakıldığında getiri +%100 olur. Beklenen
     * davranış: {@link Labeler#labelFor} %5 eşiğini aşan bu getiri için
     * {@link Labeler#BUY} sınıfını döndürür.
     */
    @Test
    void labelerAssignsClasses() {
        List<Bar> bars = new ArrayList<>();
        for (int i = 0; i < 30; i++) bars.add(new Bar("d" + i, 100.0, 1000));
        bars.set(25, new Bar("d25", 200.0, 1000));
        int lbl = Labeler.labelFor(bars, 5, 20);
        assertEquals(Labeler.BUY, lbl);
    }

    /**
     * Gerçekçi Yahoo chart JSON gövdesinden kapanış ve hacimlerin ayrıştırılması.
     * <p>
     * İki zaman damgalı mum içeren {@code chart.result[0].indicators.quote[0]}
     * metin bloğu beslenir. Beklenen davranış: {@link YahooClient#parsePrices}
     * boş olmayan bir bar listesi döndürür, ilk barın kapanışı 240.5 ve hacmi
     * 1200 olarak okunur; ikinci bar da aynı düzende listeye katılır.
     */
    @Test
    void priceScraperParsesYahooFixture() {
        String json = """
                {"chart":{"result":[{"timestamp":[1718640000,1718726400],
                "indicators":{"quote":[{"open":[240.0,238.0],"high":[241.0,239.0],
                "low":[239.0,237.0],"close":[240.5,238.5],"volume":[1200,900]}]}}]}}""";
        List<Bar> bars = new YahooClient().parsePrices(json);
        assertFalse(bars.isEmpty());
        assertEquals(240.5, bars.getFirst().close(), 0.001);
    }
}
