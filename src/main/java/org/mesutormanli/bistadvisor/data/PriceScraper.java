package org.mesutormanli.bistadvisor.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.mesutormanli.bistadvisor.config.AppConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Yahoo Finance uzerinden gecmis gunluk OHLCV serisi ceker.
 * BIST sembolleri "THYAO.IS" formatiyla sorgulanir.
 *
 * Donen her satir: tarih,acilis,yuksek,dusuk,kapanis,hacim
 */
@Component
public class PriceScraper {
    private static final Logger log = LoggerFactory.getLogger(PriceScraper.class);
    private static final String BASE = "https://query2.finance.yahoo.com/v8/finance/chart/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int MAX_RETRIES = 4;
    private final int timeoutMs;
    private final int delayMs;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public PriceScraper(AppConfig appConfig) {
        this.timeoutMs = appConfig.scrapeTimeoutMs();
        this.delayMs = appConfig.scrapeDelayMs();
        this.http = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofMillis(timeoutMs))
                .build();
    }

    /** BIST sembolunu Yahoo formatina cevirir (THYAO -> THYAO.IS). */
    public static String toYahooSymbol(String symbol) {
        String s = symbol.toUpperCase().trim();
        if (s.contains(".")) return s;            // zaten THYAO.IS gibi
        if (s.contains(":")) return s.replace(":", "."); // IS:THYAO -> IS.THYAO (nadiren)
        return s + ".IS";
    }

    public List<String> fetch(String symbol) {
        String ySymbol = toYahooSymbol(symbol);
        String url = BASE + ySymbol + "?range=1y&interval=1d";
        try {
            HttpResponse<String> res = sendWithRetry(url);
            if (res == null || res.statusCode() != 200) {
                int code = res == null ? -1 : res.statusCode();
                log.warn("Yahoo fiyat hatasi ({}): HTTP {}", symbol, code);
                return new ArrayList<>();
            }
            return parse(res.body());
        } catch (Exception e) {
            log.warn("Yahoo fiyat scrape hatasi ({}): {}", symbol, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Coklu sembol icin fiyat serisi ceker. Her istek arasinda kucuk bir gecikme
     * ekleyerek Yahoo tarafindan rate-limit (429) ile engellenmeyi azaltir.
     */
    public java.util.Map<String, List<String>> fetchAll(List<String> symbols) {
        java.util.Map<String, List<String>> out = new java.util.LinkedHashMap<>();
        for (int i = 0; i < symbols.size(); i++) {
            out.put(symbols.get(i), fetch(symbols.get(i)));
            if (delayMs > 0 && i < symbols.size() - 1) {
                try { Thread.sleep(delayMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        return out;
    }

    /**
     * Yuksek talep (30 BIST-30 hissesi) durumunda Yahoo 429 dondurebilir.
     * 429/5xx icin Retry-After (veya katlanarak backoff) ile yeniden dener.
     */
    private HttpResponse<String> sendWithRetry(String url) throws Exception {
        long backoff = 500;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(java.time.Duration.ofMillis(timeoutMs))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            int code = res.statusCode();
            if (code == 200) return res;
            if (code == 429 || code >= 500) {
                String retryAfter = res.headers().firstValue("Retry-After").orElse(null);
                long wait = backoff;
                if (retryAfter != null) {
                    try { wait = Long.parseLong(retryAfter) * 1000; } catch (NumberFormatException ignored) {}
                }
                log.warn("Yahoo gecici hata HTTP {} ({} ms bekleniyor, deneme {}/{})", code, wait, attempt + 1, MAX_RETRIES);
                Thread.sleep(wait);
                backoff *= 2;
                continue;
            }
            return res; // 404 gibi kalici hata: dogrudan don
        }
        return null;
    }

    public List<String> parse(String json) {
        List<String> rows = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode result = root.path("chart").path("result");
            if (result.isMissingNode() || !result.isArray() || result.size() == 0) return rows;
            JsonNode r = result.get(0);
            JsonNode timestamps = r.path("timestamp");
            JsonNode quotes = r.path("indicators").path("quote").get(0);
            JsonNode opens = quotes.path("open");
            JsonNode highs = quotes.path("high");
            JsonNode lows = quotes.path("low");
            JsonNode closes = quotes.path("close");
            JsonNode vols = quotes.path("volume");
            int n = timestamps.size();
            for (int i = 0; i < n; i++) {
                if (i >= closes.size() || closes.get(i).isNull()) continue;
                LocalDate d = Instant.ofEpochSecond(timestamps.get(i).asLong())
                        .atZone(ZoneId.of("Europe/Istanbul")).toLocalDate();
                double open = safe(opens, i);
                double high = safe(highs, i);
                double low = safe(lows, i);
                double close = closes.get(i).asDouble();
                double vol = i < vols.size() && !vols.get(i).isNull() ? vols.get(i).asDouble() : 0.0;
                rows.add(d + "," + open + "," + high + "," + low + "," + close + "," + (long) vol);
            }
        } catch (Exception e) {
            log.warn("Yahoo JSON parse hatasi: {}", e.getMessage());
        }
        return rows;
    }

    private double safe(JsonNode arr, int i) {
        if (i >= arr.size()) return 0.0;
        JsonNode v = arr.get(i);
        return v == null || v.isNull() ? 0.0 : v.asDouble();
    }
}
