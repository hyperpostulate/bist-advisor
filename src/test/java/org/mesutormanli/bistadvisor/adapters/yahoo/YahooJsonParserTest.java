package org.mesutormanli.bistadvisor.adapters.yahoo;

import org.junit.jupiter.api.Test;
import org.mesutormanli.bistadvisor.domain.market.Bar;
import org.mesutormanli.bistadvisor.domain.market.Fundamentals;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link YahooJsonParser} saf JSON ayrıştırmasının testleri: v8 chart fiyat
 * gövdelerinden bar üretimi (boş kapanışların atlanması, eksik hacmin sıfır
 * sayılması) ve v10 quoteSummary temel gösterge okuması (yedek alanlar, ham
 * alan dışı değerler, bozuk gövdeler).
 * <p>
 * Ayrıştırıcı ağ erişmez; testler metin gövdeleriyle beslenir ve
 * deterministiktir. Zaman damgaları İstanbul saat diliminde tarihe çevrilir.
 */
class YahooJsonParserTest {

    /**
     * Boş kapanışlı mumların atlanması ve eksik hacmin sıfır sayılması.
     * <p>
     * Senaryo: iki zaman damgalı gövde beslenir; ikinci mumun kapanışı ve hacmi
     * {@code null}'dur. Beklenen davranış: yalnız ilk mum bara dönüşür, tarihi
     * {@code 2024-01-01} (İstanbul), kapanışı 240.5 ve hacmi 1200'tür; ikinci
     * mum atlanır. Hacim alanı hiç yoksa hacim 0 okunur.
     */
    @Test
    void bosKapanislarAtlanirEksikHacimSifirdir() {
        String json = """
                {"chart":{"result":[{"timestamp":[1704067200,1704153600],
                "indicators":{"quote":[{"close":[240.5,null],"volume":[1200,null]}]}}]}}""";

        List<Bar> bars = YahooJsonParser.parsePrices(json);

        assertEquals(1, bars.size());
        assertEquals("2024-01-01", bars.getFirst().date());
        assertEquals(240.5, bars.getFirst().close(), 1e-9);
        assertEquals(1200, bars.getFirst().volume(), 1e-9);

        List<Bar> noVolume = YahooJsonParser.parsePrices("""
                {"chart":{"result":[{"timestamp":[1704067200],
                "indicators":{"quote":[{"close":[240.5]}]} }]}}""");
        assertEquals(1, noVolume.size());
        assertEquals(0, noVolume.getFirst().volume(), 1e-9);
    }

    /**
     * Temel göstergelerin ilgili modüllerden okunması.
     * <p>
     * Senaryo: {@code summaryDetail}/{@code financialData} alanları dolu gövde
     * beslenir. Beklenen davranış: F/K 8,5 ({@code trailingPE}), PD/DD 1,2,
     * temettü verimi 0,03, kâr büyümesi 0,2 ve ROE 0,15 olarak okunur.
     */
    @Test
    void temelGostergelerModullerdenOkunur() {
        String json = """
                {"quoteSummary":{"result":[{"summaryDetail":{
                "trailingPE":{"raw":8.5},"priceToBook":{"raw":1.2},"dividendYield":{"raw":0.03}},
                "defaultKeyStatistics":{"forwardPE":{"raw":9.0}},
                "financialData":{"returnOnEquity":{"raw":0.15},
                "earningsGrowth":{"raw":0.2},"revenueGrowth":{"raw":0.1}}}]}}""";

        Fundamentals f = YahooJsonParser.parseFundamentals(json);

        assertEquals(8.5, f.fk(), 1e-9);
        assertEquals(1.2, f.pdDd(), 1e-9);
        assertEquals(0.03, f.dividendYield(), 1e-9);
        assertEquals(0.2, f.profitGrowth(), 1e-9);
        assertEquals(0.15, f.roe(), 1e-9);
    }

    /**
     * Yedek alanlara düşülmesi ve ham olmayan değerlerin sıfır sayılması.
     * <p>
     * Senaryo: {@code trailingPE} −1 (≤ 0) ve {@code earningsGrowth} 0 verilir;
     * PD/DD alanı ham içermez. Beklenen davranış: F/K yedek {@code forwardPE}
     * (9,0) ile, kâr büyümesi yedek {@code revenueGrowth} (0,1) ile okunur ve
     * PD/DD 0 kalır.
     */
    @Test
    void yedekAlanlaraDusulurHamOlmayanDegerlerSifirdir() {
        String json = """
                {"quoteSummary":{"result":[{"summaryDetail":{
                "trailingPE":{"raw":-1.0},"priceToBook":{"fmt":"1.2"}},
                "defaultKeyStatistics":{"forwardPE":{"raw":9.0}},
                "financialData":{"returnOnEquity":{"raw":0.15},
                "earningsGrowth":{"raw":0},"revenueGrowth":{"raw":0.1}}}]}}""";

        Fundamentals f = YahooJsonParser.parseFundamentals(json);

        assertEquals(9.0, f.fk(), 1e-9);
        assertEquals(0.0, f.pdDd(), 1e-9);
        assertEquals(0.1, f.profitGrowth(), 1e-9);
    }

    /**
     * Bozuk/eksik gövdelerde istisna yerine varsayılan sonuç döndürülmesi.
     * <p>
     * Senaryo: geçersiz JSON ve sonuç düğümü içermeyen gövdeler beslenir.
     * Beklenen davranış: hiçbir çağrı istisna fırlatmaz; fiyatlar boş liste,
     * temel göstergeler {@link Fundamentals#EMPTY} döner.
     */
    @Test
    void bozukGovdelerIstisnaYaymaz() {
        assertTrue(YahooJsonParser.parsePrices("bozuk").isEmpty());
        assertTrue(YahooJsonParser.parsePrices("{}").isEmpty());
        assertEquals(Fundamentals.EMPTY, YahooJsonParser.parseFundamentals("bozuk"));
        assertEquals(Fundamentals.EMPTY, YahooJsonParser.parseFundamentals("{}"));
    }
}