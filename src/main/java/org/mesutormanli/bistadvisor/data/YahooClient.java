package org.mesutormanli.bistadvisor.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mesutormanli.bistadvisor.features.TechnicalFeatures.Bar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Yahoo Finance API'den fiyat (OHLCV) ve temel veri (F/K, PD/DD, temettu, ROE, buyume) ceker. */
@Component
public class YahooClient {
    private static final Logger log = LoggerFactory.getLogger(YahooClient.class);
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String PRICE_URL = "https://query2.finance.yahoo.com/v8/finance/chart/%s?range=1y&interval=1d";
    private static final String FUND_URL = "https://query2.finance.yahoo.com/v10/finance/quoteSummary/%s?modules=summaryDetail,defaultKeyStatistics,financialData,price&crumb=%s";
    private static final String CRUMB_URL = "https://query2.finance.yahoo.com/v1/test/getcrumb";
    private static final String COOKIE_URL = "https://fc.yahoo.com";
    private static final int MAX_RETRIES = 3;

    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile String crumb;

    public YahooClient() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** 5 temel finansal gosterge: F/K, PD/DD, temettu verimi, kar buyumesi, ROE. */
    public record Fundamentals(double fk, double pdDd, double dividendYield, double profitGrowth, double roe) {
        public static final Fundamentals EMPTY = new Fundamentals(0, 0, 0, 0, 0);
    }

    /** BIST sembolunu Yahoo formatina cevirir (THYAO -> THYAO.IS). */
    public static String yahooSymbol(String symbol) {
        String s = symbol.toUpperCase().trim();
        if (s.contains(".")) return s;
        return s + ".IS";
    }

    /** Bir sembol icin 1 yillik gunluk fiyat serisini Yahoo Finance'den ceker. */
    public List<Bar> fetchPrices(String symbol) {
        String url = String.format(PRICE_URL, yahooSymbol(symbol));
        try {
            HttpResponse<String> res = get(url, false);
            if (res == null || res.statusCode() != 200) return List.of();
            return parsePrices(res.body());
        } catch (Exception e) {
            log.warn("fiyat hatasi {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    /** Bir sembol icin temel finansal gostergeleri Yahoo Finance'den ceker. */
    public Fundamentals fetchFundamentals(String symbol) {
        try {
            ensureCrumb();
            String url = String.format(FUND_URL, yahooSymbol(symbol), crumb);
            HttpResponse<String> res = get(url, true);
            if (res == null || res.statusCode() != 200) return Fundamentals.EMPTY;
            return parseFundamentals(res.body());
        } catch (Exception e) {
            log.warn("temel veri hatasi {}: {}", symbol, e.getMessage());
            return Fundamentals.EMPTY;
        }
    }

    /** Yahoo quoteSummary API'si icin gerekli crumb degerini cookie ile birlikte alir. */
    private synchronized void ensureCrumb() throws Exception {
        if (crumb != null) return;
        HttpResponse<Void> cr = http.send(HttpRequest.newBuilder()
                .uri(URI.create(COOKIE_URL)).timeout(Duration.ofSeconds(15))
                .header("User-Agent", UA).GET().build(), HttpResponse.BodyHandlers.discarding());
        String cookie = cookieString(cr.headers());
        HttpResponse<String> rr = http.send(HttpRequest.newBuilder()
                .uri(URI.create(CRUMB_URL)).timeout(Duration.ofSeconds(15))
                .header("User-Agent", UA).header("Cookie", cookie).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (rr.statusCode() != 200 || rr.body().isBlank())
            throw new IllegalStateException("crumb alinamadi: HTTP " + rr.statusCode());
        crumb = rr.body().trim();
    }

    /** HTTP response header'larindaki set-cookie degerlerini birlestirir. */
    private String cookieString(java.net.http.HttpHeaders headers) {
        StringBuilder sb = new StringBuilder();
        for (var e : headers.map().entrySet()) {
            if (e.getKey().equalsIgnoreCase("set-cookie")) {
                for (String c : e.getValue()) {
                    if (c != null && !c.isBlank()) {
                        if (sb.length() > 0) sb.append("; ");
                        sb.append(c.split(";")[0]);
                    }
                }
            }
        }
        return sb.toString();
    }

    /** HTTP GET istegi gonderir, 429/5xx'te katlanarak backoff ile yeniden dener. */
    private HttpResponse<String> get(String url, boolean withCrumb) throws Exception {
        long backoff = 500;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            var builder = HttpRequest.newBuilder()
                    .uri(URI.create(url)).timeout(Duration.ofSeconds(15))
                    .header("User-Agent", UA).header("Accept", "application/json");
            if (withCrumb) builder.header("Cookie", cookieString(null));
            HttpRequest req = builder.GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            int code = res.statusCode();
            if (code == 200) return res;
            if (code == 401 && withCrumb) {
                synchronized (this) { crumb = null; ensureCrumb(); }
                continue;
            }
            if (code == 429 || code >= 500) {
                Thread.sleep(backoff);
                backoff *= 2;
                continue;
            }
            return res;
        }
        return null;
    }

    /** Yahoo Finance v8 chart JSON'indan {tarih, kapanis, hacim} Bar listesi olusturur. */
    public List<Bar> parsePrices(String json) {
        List<Bar> bars = new ArrayList<>();
        try {
            JsonNode r = mapper.readTree(json).path("chart").path("result").get(0);
            JsonNode ts = r.path("timestamp");
            JsonNode q = r.path("indicators").path("quote").get(0);
            JsonNode closes = q.path("close");
            JsonNode vols = q.path("volume");
            for (int i = 0; i < ts.size(); i++) {
                if (closes.get(i) == null || closes.get(i).isNull()) continue;
                LocalDate d = Instant.ofEpochSecond(ts.get(i).asLong())
                        .atZone(ZoneId.of("Europe/Istanbul")).toLocalDate();
                double vol = i < vols.size() && vols.get(i) != null && !vols.get(i).isNull() ? vols.get(i).asDouble() : 0;
                bars.add(new Bar(d.toString(), closes.get(i).asDouble(), vol));
            }
        } catch (Exception e) {
            log.warn("JSON parse hatasi: {}", e.getMessage());
        }
        return bars;
    }

    /** Yahoo Finance quoteSummary JSON'indan Fundamentals record olusturur. */
    Fundamentals parseFundamentals(String json) {
        try {
            JsonNode r = mapper.readTree(json).path("quoteSummary").path("result").get(0);
            double fk = node(r, "summaryDetail", "trailingPE");
            if (fk <= 0) fk = node(r, "defaultKeyStatistics", "forwardPE");
            double pdDd = node(r, "summaryDetail", "priceToBook");
            double dy = node(r, "summaryDetail", "dividendYield");
            double roe = node(r, "financialData", "returnOnEquity");
            double pg = node(r, "financialData", "earningsGrowth");
            if (pg == 0) pg = node(r, "financialData", "revenueGrowth");
            return new Fundamentals(fk, pdDd, dy, pg, roe);
        } catch (Exception e) {
            log.warn("temel JSON parse hatasi: {}", e.getMessage());
            return Fundamentals.EMPTY;
        }
    }

    /** Yahoo JSON'undaki "section.field.raw" degerini okur, yoksa 0 doner. */
    private double node(JsonNode parent, String section, String field) {
        JsonNode n = parent.path(section).path(field).path("raw");
        return n.isNumber() ? n.asDouble() : 0.0;
    }
}
