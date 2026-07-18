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
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Yahoo Finance uzerinden temel finansal verileri ceker (quoteSummary).
 * Crumb + cookie gerektirdigi icin oturum basina bir kez alinir.
 *
 * Cikan temel alanlar: fk (F/K), pdDd (PD/DD), dividendYield,
 * profitGrowth, roe -- FeatureVector ile uyumlu.
 */
@Component
public class YahooFundamentalsScraper {
    private static final Logger log = LoggerFactory.getLogger(YahooFundamentalsScraper.class);
    private static final String BASE = "https://query2.finance.yahoo.com/v10/finance/quoteSummary/";
    private static final String CRUMB_URL = "https://query2.finance.yahoo.com/v1/test/getcrumb";
    private static final String COOKIE_URL = "https://fc.yahoo.com";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String MODULES = "modules=summaryDetail,defaultKeyStatistics,financialData,price";
    private static final int MAX_RETRIES = 3;

    private final int timeoutMs;
    private final int delayMs;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private String crumb;
    private java.net.http.HttpHeaders cookieHeaders;

    public YahooFundamentalsScraper(AppConfig appConfig) {
        this.timeoutMs = appConfig.scrapeTimeoutMs();
        this.delayMs = appConfig.scrapeDelayMs();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** BIST sembolunu Yahoo formatina cevirir (THYAO -> THYAO.IS). */
    public static String toYahooSymbol(String symbol) {
        String s = symbol.toUpperCase().trim();
        if (s.contains(".")) return s;
        if (s.contains(":")) return s.replace(":", ".");
        return s + ".IS";
    }

    /** Tek bir sembol icin temel verileri dondurur. Hata/eksikse bos map. */
    public Map<String, Double> fetch(String symbol) {
        Map<String, Double> out = new HashMap<>();
        String ySymbol = toYahooSymbol(symbol);
        try {
            ensureCrumb();
            String url = BASE + ySymbol + "?" + MODULES + "&crumb=" + crumb;
            HttpResponse<String> res = sendWithRetry(url);
            if (res == null || res.statusCode() != 200) {
                int code = res == null ? -1 : res.statusCode();
                log.warn("Yahoo temel veri hatasi ({}): HTTP {}", symbol, code);
                return out;
            }
            parse(res.body(), out);
        } catch (Exception e) {
            log.warn("Yahoo temel veri scrape hatasi ({}): {}", symbol, e.getMessage());
        }
        return out;
    }

    private void ensureCrumb() throws Exception {
        if (crumb != null) return;
        // 1) cookie al
        HttpRequest cookieReq = HttpRequest.newBuilder()
                .uri(URI.create(COOKIE_URL))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("User-Agent", USER_AGENT)
                .GET().build();
        HttpResponse<Void> cookieRes = http.send(cookieReq, HttpResponse.BodyHandlers.discarding());
        cookieHeaders = cookieRes.headers();
        // 2) crumb al
        HttpRequest crumbReq = HttpRequest.newBuilder()
                .uri(URI.create(CRUMB_URL))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("User-Agent", USER_AGENT)
                .headers("Cookie", cookieString())
                .GET().build();
        HttpResponse<String> crumbRes = http.send(crumbReq, HttpResponse.BodyHandlers.ofString());
        if (crumbRes.statusCode() != 200 || crumbRes.body().isBlank()) {
            throw new IllegalStateException("Crumb alinamadi: HTTP " + crumbRes.statusCode());
        }
        crumb = crumbRes.body().trim();
    }

    private String cookieString() {
        if (cookieHeaders == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> e : cookieHeaders.map().entrySet()) {
            if (e.getKey().equalsIgnoreCase("set-cookie")) {
                for (String c : e.getValue()) {
                    if (c != null && !c.isBlank()) {
                        String pair = c.split(";")[0];
                        if (sb.length() > 0) sb.append("; ");
                        sb.append(pair);
                    }
                }
            }
        }
        return sb.toString();
    }

    private HttpResponse<String> sendWithRetry(String url) throws Exception {
        long backoff = 500;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .headers("Cookie", cookieString())
                    .GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            int code = res.statusCode();
            if (code == 200) return res;
            if (code == 401) { // crumb gecersiz oldu, yenile
                crumb = null;
                ensureCrumb();
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

    private void parse(String json, Map<String, Double> out) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode result = root.path("quoteSummary").path("result");
            if (result.isMissingNode() || !result.isArray() || result.size() == 0) return;
            JsonNode r = result.get(0);

            double fk = node(r, "summaryDetail", "trailingPE");
            if (fk <= 0) fk = node(r, "defaultKeyStatistics", "forwardPE");
            double pdDd = node(r, "summaryDetail", "priceToBook");
            double dy = node(r, "summaryDetail", "dividendYield");
            double roe = node(r, "financialData", "returnOnEquity");
            double profitGrowth = node(r, "financialData", "earningsGrowth");
            if (profitGrowth == 0) profitGrowth = node(r, "financialData", "revenueGrowth");

            out.put("fk", fk);
            out.put("pdDd", pdDd);
            out.put("dividendYield", dy);
            out.put("profitGrowth", profitGrowth);
            out.put("roe", roe);
        } catch (Exception e) {
            log.warn("Yahoo temel JSON parse hatasi: {}", e.getMessage());
        }
    }

    /** Yahoo "raw" icinde deger, yoksa 0. */
    private double node(JsonNode parent, String section, String field) {
        JsonNode n = parent.path(section).path(field).path("raw");
        return n.isNumber() ? n.asDouble() : 0.0;
    }
}
