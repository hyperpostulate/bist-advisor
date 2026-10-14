package org.mesutormanli.bistadvisor.adapters.yahoo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mesutormanli.bistadvisor.domain.market.Bar;
import org.mesutormanli.bistadvisor.domain.market.Fundamentals;
import org.mesutormanli.bistadvisor.domain.market.MarketTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Yahoo Finance JSON yanıt gövdelerini alan nesnelerine dönüştüren saf ayrıştırıcı.
 * <p>
 * Tek sorumluluğu JSON → {@link Bar}/{@link Fundamentals} dönüşümüdür; ağ erişimi ve
 * önbellekleme yapmaz, bu yüzden birim test edilebilir. Tüm üyeler statiktir.
 * Ayrıştırma hataları istisna yaymaz, boş/varsayılan sonuç döner ve günlüğe yazılır.
 */
public final class YahooJsonParser {

    private static final Logger log = LoggerFactory.getLogger(YahooJsonParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private YahooJsonParser() {}

    /**
     * v8 chart JSON gövdesinden fiyat çubuklarını ayrıştırır.
     * <p>
     * Epoch saniye cinsinden zaman damgaları {@link MarketTime#ZONE} (İstanbul) üzerinden tarihe
     * çevrilir; kapanışı {@code null} olan çubuklar atlanır, hacim alanı yoksa 0 kullanılır.
     *
     * @param json v8 chart yanıt gövdesi
     * @return bar listesi; ayrıştırma hatasında boş liste
     */
    public static List<Bar> parsePrices(String json) {
        List<Bar> bars = new ArrayList<>();
        try {
            JsonNode r = MAPPER.readTree(json).path("chart").path("result").get(0);
            JsonNode ts = r.path("timestamp");
            JsonNode q = r.path("indicators").path("quote").get(0);
            JsonNode closes = q.path("close");
            JsonNode vols = q.path("volume");
            for (int i = 0; i < ts.size(); i++) {
                if (closes.get(i) == null || closes.get(i).isNull()) continue;
                LocalDate d = Instant.ofEpochSecond(ts.get(i).asLong())
                        .atZone(MarketTime.ZONE).toLocalDate();
                double vol = i < vols.size() && vols.get(i) != null && !vols.get(i).isNull() ? vols.get(i).asDouble() : 0;
                bars.add(new Bar(d.toString(), closes.get(i).asDouble(), vol));
            }
        } catch (Exception e) {
            log.warn("JSON parse hatasi: {}", e.getMessage());
        }
        return bars;
    }

    /**
     * v10 quoteSummary JSON gövdesinden temel göstergeleri ayrıştırır.
     * <p>
     * F/K için {@code trailingPE}, ≤ 0 ise {@code forwardPE}; kâr büyümesi için
     * {@code earningsGrowth}, 0 ise {@code revenueGrowth} okunur. PD/DD, temettü verimi ve
     * özkaynak kârlılığı (ROE) ilgili modüllerin {@code raw} alanlarından alınır. Tüm
     * alanlar 0'a düşerse (Yahoo şeması değişmiş olabilir) {@code WARN} seviyesinde
     * günlüğe yazılır; böylece sessiz bozulma fark edilir.
     *
     * @param json v10 quoteSummary yanıt gövdesi
     * @return temel göstergeler; ayrıştırma hatasında {@link Fundamentals#EMPTY}
     */
    public static Fundamentals parseFundamentals(String json) {
        try {
            JsonNode r = MAPPER.readTree(json).path("quoteSummary").path("result").get(0);
            double fk = node(r, "summaryDetail", "trailingPE");
            if (fk <= 0) fk = node(r, "defaultKeyStatistics", "forwardPE");
            double pdDd = node(r, "summaryDetail", "priceToBook");
            double dy = node(r, "summaryDetail", "dividendYield");
            double roe = node(r, "financialData", "returnOnEquity");
            double pg = node(r, "financialData", "earningsGrowth");
            if (pg == 0) pg = node(r, "financialData", "revenueGrowth");
            Fundamentals f = new Fundamentals(fk, pdDd, dy, pg, roe);
            if (f.equals(Fundamentals.EMPTY)) {
                log.warn("Temel gostergelerin tamami okunamadi (tumu 0); Yahoo semasi "
                        + "degismis ya da beklenen alanlar (trailingPE/priceToBook/dividendYield/"
                        + "earningsGrowth/returnOnEquity) kaldirilmis olabilir");
            }
            return f;
        } catch (Exception e) {
            log.warn("temel JSON parse hatasi: {}", e.getMessage());
            return Fundamentals.EMPTY;
        }
    }

    /**
     * JSON bölümündeki alanın {@code raw} sayısal değerini okur.
     *
     * @param parent  üst JSON düğümü
     * @param section modül adı (ör. {@code summaryDetail})
     * @param field   alan adı (ör. {@code trailingPE})
     * @return {@code raw} değer; sayısal değilse {@code 0.0}
     */
    private static double node(JsonNode parent, String section, String field) {
        JsonNode n = parent.path(section).path(field).path("raw");
        return n.isNumber() ? n.asDouble() : 0.0;
    }
}