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

/**
 * Yahoo Finance API'den hisse senedi fiyat ve temel verilerini çeken HTTP istemcisi.
 * <p>
 * İsteklerde crumb-tabanlı kimlik doğrulama kullanır; 401 hatası durumunda crumb'ı
 * yeniler. 429 (rate-limit) ve 5xx hatalarında üstel geri çekilme (exponential backoff)
 * uygular.
 */
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
    private volatile String sessionCookie;

    /**
     * {@code YahooClient} HTTP istemcisini kurar. Bağlantı zaman aşımı 15 saniye
     * olarak ayarlanır ve yönlendirmelere izin verilir.
     */
    public YahooClient() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Temel veri (F/K, PD/DD, temettü verimi, büyüme, özkaynak karlılığı) taşıyan
     * veri kaydı.
     *
     * @param fk            Fiyat/Kazanç oranı (trailing PE veya forward PE)
     * @param pdDd          PD/DD oranı (Price to Book)
     * @param dividendYield temettü verimi
     * @param profitGrowth  kâr büyüme oranı (earningsGrowth veya revenueGrowth)
     * @param roe           özkaynak karlılığı (Return on Equity)
     */
    public record Fundamentals(double fk, double pdDd, double dividendYield, double profitGrowth, double roe) {
        /**
         * Hiçbir veri alınamadığında kullanılacak boş (sıfır) fundamentals kaydı.
         */
        public static final Fundamentals EMPTY = new Fundamentals(0, 0, 0, 0, 0);
    }

    /**
     * Bir BIST sembolünü Yahoo Finance formatına dönüştürür (ör. {@code "AKBNK"} ->
     * {@code "AKBNK.IS"}). Eğer sembol zaten nokta içeriyorsa olduğu gibi döndürülür.
     *
     * @param symbol yerel hisse sembolü
     * @return Yahoo Finance sembolü ({@code SEMBOL.IS})
     */
    public static String yahooSymbol(String symbol) {
        String s = symbol.toUpperCase().trim();
        if (s.contains(".")) return s;
        return s + ".IS";
    }

    /**
     * Yahoo Finance'den 1 yıllık günlük fiyat verisini çeker.
     *
     * @param symbol hisse sembolü
     * @return kapanış fiyatı ve hacim içeren {@link Bar} listesi
     */
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

    /**
     * Yahoo Finance'den temel verileri (F/K, PD/DD, temettü vb.) çeker.
     *
     * @param symbol hisse sembolü
     * @return {@link Fundamentals} kaydı, hata durumunda {@link Fundamentals#EMPTY}
     */
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

    /**
     * Yahoo Finance API için gerekli crumb (güvenlik anahtarı) ve oturum çerezini
     * alır. İlk çağrıda çerez ve crumb alınır; sonraki çağrılarda önbellekten
     * kullanılır.
     *
     * @throws Exception crumb alınamazsa
     */
    private synchronized void ensureCrumb() throws Exception {
        if (crumb != null) return;
        HttpResponse<Void> cr = http.send(HttpRequest.newBuilder()
                .uri(URI.create(COOKIE_URL)).timeout(Duration.ofSeconds(15))
                .header("User-Agent", UA).GET().build(), HttpResponse.BodyHandlers.discarding());
        sessionCookie = cookieString(cr.headers());
        HttpResponse<String> rr = http.send(HttpRequest.newBuilder()
                .uri(URI.create(CRUMB_URL)).timeout(Duration.ofSeconds(15))
                .header("User-Agent", UA).header("Cookie", sessionCookie).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (rr.statusCode() != 200 || rr.body().isBlank())
            throw new IllegalStateException("crumb alinamadi: HTTP " + rr.statusCode());
        crumb = rr.body().trim();
    }

    /**
     * HTTP yanıt başlıklarından {@code Set-Cookie} değerlerini ayıklar ve
     * {@code "ad=deger; ad2=deger2"}} formatında bir çerez dizesi oluşturur.
     *
     * @param headers HTTP yanıt başlıkları
     * @return birleştirilmiş çerez dizesi
     */
    private String cookieString(java.net.http.HttpHeaders headers) {
        StringBuilder sb = new StringBuilder();
        for (var e : headers.map().entrySet()) {
            if (e.getKey().equalsIgnoreCase("set-cookie")) {
                for (String c : e.getValue()) {
                    if (c != null && !c.isBlank()) {
                        if (!sb.isEmpty()) sb.append("; ");
                        sb.append(c.split(";")[0]);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * HTTP GET isteği gönderir. 401 hatasında crumb yenilenir; 429 ve 5xx
     * hatalarında üstel geri çekilme (exponential backoff) uygulanır.
     *
     * @param url       istek URL'si
     * @param withCrumb crumb başlığı eklensin mi?
     * @return HTTP yanıtı, maksimum deneme sonrası hata durumunda {@code null}
     * @throws Exception istek sırasında oluşan hata
     */
    private HttpResponse<String> get(String url, boolean withCrumb) throws Exception {
        long backoff = 500;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            var builder = HttpRequest.newBuilder()
                    .uri(URI.create(url)).timeout(Duration.ofSeconds(15))
                    .header("User-Agent", UA).header("Accept", "application/json");
            if (withCrumb && sessionCookie != null) builder.header("Cookie", sessionCookie);
            HttpRequest req = builder.GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            int code = res.statusCode();
            if (code == 200) return res;
            if (code == 401 && withCrumb) {
                synchronized (this) { crumb = null; sessionCookie = null; ensureCrumb(); }
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

    /**
     * Yahoo Finance JSON yanıtından fiyat çubuklarını ayrıştırır.
     * Timestamp, close ve volume alanlarını okur; null değerler atlanır.
     *
     * @param json ham JSON dizesi
     * @return ayrıştırılmış {@link Bar} listesi
     */
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

    /**
     * Yahoo Finance JSON yanıtından temel verileri ayrıştırır.
     * F/K, PD/DD, temettü verimi, özkaynak karlılığı ve büyüme oranlarını okur.
     *
     * @param json ham JSON dizesi
     * @return ayrıştırılmış {@link Fundamentals} kaydı
     */
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

    /**
     * JSON ağacında {@code parent.section.field.raw} yolundaki sayısal değeri döndürür.
     * Değer sayı değilse ya da yol yoksa {@code 0.0} döner.
     *
     * @param parent  kök JSON düğümü
     * @param section bölüm adı (ör. {@code "summaryDetail"})
     * @param field   alan adı (ör. {@code "trailingPE"})
     * @return sayısal değer veya 0.0
     */
    private double node(JsonNode parent, String section, String field) {
        JsonNode n = parent.path(section).path(field).path("raw");
        return n.isNumber() ? n.asDouble() : 0.0;
    }
}
