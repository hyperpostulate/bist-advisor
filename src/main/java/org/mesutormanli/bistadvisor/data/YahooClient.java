package org.mesutormanli.bistadvisor.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mesutormanli.bistadvisor.features.TechnicalFeatures.Bar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Yahoo Finance REST uçlarından günlük fiyat ve temel gösterge verilerini çeken HTTP istemcisi.
 * <p>
 * Günlük fiyatlar v8 chart ucundan ({@code range=1y&interval=1d}), temel göstergeler ise v10
 * quoteSummary ucunun {@code summaryDetail}, {@code defaultKeyStatistics}, {@code financialData} ve
 * {@code price} modülleriyle alınır. Yetkilendirme oturum çerezi ve crumb ile yapılır; 401 yanıtlarında
 * crumb/çerez sıfırlanır, 429 ve 5xx yanıtlarında üstel geri çekilme uygulanır. İstekler arası gecikme
 * {@code bist.scrape.delay-ms} (varsayılan 250 ms), istek zaman aşımı ise {@code bist.scrape.timeout-ms}
 * (varsayılan 15 sn) ile belirlenir. Temel göstergeler gün bazlı bellek içi önbellekte tutulur.
 */
@Component
public class YahooClient {
    private static final Logger log = LoggerFactory.getLogger(YahooClient.class);
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String PRICE_URL = "https://query2.finance.yahoo.com/v8/finance/chart/%s?range=1y&interval=1d";
    private static final String FUND_URL = "https://query2.finance.yahoo.com/v10/finance/quoteSummary/%s?modules=summaryDetail,defaultKeyStatistics,financialData,price";
    private static final String CRUMB_URL = "https://query2.finance.yahoo.com/v1/test/getcrumb";
    private static final String COOKIE_URL = "https://fc.yahoo.com";
    private static final int MAX_RETRIES = 3;

    @org.springframework.beans.factory.annotation.Value("${bist.scrape.delay-ms:250}")
    private long requestDelayMs = 250;

    @org.springframework.beans.factory.annotation.Value("${bist.scrape.timeout-ms:15000}")
    private long requestTimeoutMs = 15000;

    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, CachedFundamentals> fundCache = new ConcurrentHashMap<>();
    private volatile String crumb;
    private volatile String sessionCookie;

    /**
     * Gün bazlı temel gösterge önbelleği girişi: {@code day} önbelleğin geçerli olduğu tarihi,
     * {@code fundamentals} o gün alınan temel göstergeleri taşır.
     *
     * @param day          önbelleğin geçerli olduğu tarih
     * @param fundamentals o gün alınan temel göstergeler
     */
    private record CachedFundamentals(LocalDate day, Fundamentals fundamentals) {}

    /**
     * Kurar ve bağlantı zaman aşımı 15 saniye olan, yönlendirmeleri izleyen HTTP istemcisini hazırlar.
     */
    public YahooClient() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Temel gösterge kümesi: F/K, PD/DD, temettü verimi, kâr büyümesi ve özkaynak kârlılığı (ROE).
     * <p>
     * Veri alınamadığını temsil eden {@link #EMPTY} örneği tüm bileşenlerinde 0 taşır.
     *
     * @param fk            F/K oranı ({@code trailingPE}; ≤ 0 ise {@code forwardPE})
     * @param pdDd          PD/DD oranı
     * @param dividendYield temettü verimi
     * @param profitGrowth  kâr büyümesi ({@code earningsGrowth}; 0 ise {@code revenueGrowth})
     * @param roe           özkaynak kârlılığı (ROE)
     */
    public record Fundamentals(double fk, double pdDd, double dividendYield, double profitGrowth, double roe) {
        public static final Fundamentals EMPTY = new Fundamentals(0, 0, 0, 0, 0);
    }

    /**
     * Sembolü Yahoo Finance sembolüne dönüştürür: nokta içermeyen sembole {@code .IS} (BIST) eki eklenir.
     *
     * @param symbol yerel hisse/endeks sembolü
     * @return Yahoo Finance sembolü; sembol nokta içeriyorsa olduğu gibi döner
     */
    public static String yahooSymbol(String symbol) {
        String s = symbol.toUpperCase().trim();
        if (s.contains(".")) return s;
        return s + ".IS";
    }

    /**
     * Sembol için son 1 yıllık günlük fiyatları v8 chart ucundan ({@code range=1y&interval=1d}) çeker.
     *
     * @param symbol hisse/endeks sembolü
     * @return kapanış ve hacim içeren {@link Bar} listesi; hata veya 200 dışı yanıtta boş liste
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
     * Sembol için temel göstergeleri (F/K, PD/DD, temettü verimi, kâr büyümesi, ROE) v10
     * quoteSummary ucundan çeker.
     * <p>
     * Sonuçlar gün bazlı bellek içi önbellekte tutulur; aynı gün içindeki tekrarlı çağrılar ağa çıkmaz.
     *
     * @param symbol hisse sembolü
     * @return temel göstergeler; hata durumunda {@link Fundamentals#EMPTY}
     */
    public Fundamentals fetchFundamentals(String symbol) {
        String ysym = yahooSymbol(symbol);
        LocalDate today = MarketTime.today();
        CachedFundamentals hit = fundCache.get(ysym);
        if (hit != null && hit.day().equals(today)) return hit.fundamentals();
        try {
            ensureCrumb();
            String url = String.format(FUND_URL, ysym);
            HttpResponse<String> res = get(url, true);
            if (res == null || res.statusCode() != 200) return Fundamentals.EMPTY;
            Fundamentals f = parseFundamentals(res.body());
            fundCache.put(ysym, new CachedFundamentals(today, f));
            return f;
        } catch (Exception e) {
            log.warn("temel veri hatasi {}: {}", symbol, e.getMessage());
            return Fundamentals.EMPTY;
        }
    }

    /**
     * Oturum çerezini ve crumb değerini alarak istek yetkilendirmesini hazırlar.
     * <p>
     * Senkronize çalışır; crumb zaten alınmışsa yeni istek yapılmaz. Crumb isteği
     * HTTP 200 ve boş olmayan gövde ister.
     *
     * @throws Exception çerez veya crumb isteği ağ hatasıyla sonuçlanırsa
     * @throws IllegalStateException crumb isteği HTTP 200 dönmez veya gövde boşsa
     */
    private synchronized void ensureCrumb() throws Exception {
        if (crumb != null) return;
        throttle();
        HttpResponse<Void> cr = http.send(HttpRequest.newBuilder()
                .uri(URI.create(COOKIE_URL)).timeout(Duration.ofMillis(requestTimeoutMs))
                .header("User-Agent", UA).GET().build(), HttpResponse.BodyHandlers.discarding());
        sessionCookie = cookieString(cr.headers());
        throttle();
        HttpResponse<String> rr = http.send(HttpRequest.newBuilder()
                .uri(URI.create(CRUMB_URL)).timeout(Duration.ofMillis(requestTimeoutMs))
                .header("User-Agent", UA).header("Cookie", sessionCookie).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (rr.statusCode() != 200 || rr.body().isBlank())
            throw new IllegalStateException("crumb alinamadi: HTTP " + rr.statusCode());
        crumb = rr.body().trim();
    }

    /**
     * İstekler arasına {@code bist.scrape.delay-ms} kadar gecikme koyar (varsayılan 250 ms).
     *
     * @implNote Gecikme sıfır/negatifse bekleme yapılmaz; kesintiye uğrarsa iş parçacığının
     *           kesme durumu korunur.
     */
    private void throttle() {
        if (requestDelayMs <= 0) return;
        try {
            Thread.sleep(requestDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Yanıt başlıklarındaki {@code Set-Cookie} değerlerini birleştirip isteklerde kullanılacak
     * çerez dizesini oluşturur ({@code "ad=deger; ad2=deger2"} biçimi).
     *
     * @param headers HTTP yanıtı başlıkları
     * @return birleştirilmiş çerez dizesi; uygun başlık yoksa boş dize
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
     * HTTP GET isteğini en fazla 3 denemede gönderir.
     * <p>
     * 200 yanıtı olduğu gibi döner. 401'de crumb/çerez sıfırlanıp yeniden denenir. 429 ve 5xx
     * yanıtlarında 500 ms ile başlayan ve her denemede ikiye katlanan üstel geri çekilme
     * uygulanır; diğer durum kodları aynen döndürülür. Denemeler tükenirse {@code null} döner.
     *
     * @param url       istek URL'si (crumb parametresi hariç)
     * @param withCrumb isteğin crumb ve oturum çereziyle yetkilendirilip yetkilendirilmeyeceği
     * @return HTTP yanıtı; tüm denemeler sonuçsuz kalırsa {@code null}
     * @throws Exception istek gönderilirken ağ veya kesinti hatası olursa
     */
    private HttpResponse<String> get(String url, boolean withCrumb) throws Exception {
        long backoff = 500;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            String finalUrl = url;
            if (withCrumb) {
                if (crumb == null) ensureCrumb();
                finalUrl = url + "&crumb=" + URLEncoder.encode(crumb, StandardCharsets.UTF_8);
            }
            throttle();
            var builder = HttpRequest.newBuilder()
                    .uri(URI.create(finalUrl)).timeout(Duration.ofMillis(requestTimeoutMs))
                    .header("User-Agent", UA).header("Accept", "application/json");
            if (withCrumb && sessionCookie != null) builder.header("Cookie", sessionCookie);
            HttpRequest req = builder.GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            int code = res.statusCode();
            if (code == 200) return res;
            if (code == 401 && withCrumb) {
                crumb = null;
                sessionCookie = null;
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
     * v8 chart JSON gövdesinden fiyat çubuklarını ayrıştırır.
     * <p>
     * Epoch saniye cinsinden zaman damgaları {@link MarketTime#ZONE} (İstanbul) üzerinden tarihe
     * çevrilir; kapanışı {@code null} olan çubuklar atlanır, hacim alanı yoksa 0 kullanılır.
     *
     * @param json v8 chart yanıt gövdesi
     * @return {@link Bar} listesi; ayrıştırma hatasında boş liste
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
     * özkaynak kârlılığı (ROE) ilgili modüllerin {@code raw} alanlarından alınır.
     *
     * @param json v10 quoteSummary yanıt gövdesi
     * @return temel göstergeler; ayrıştırma hatasında {@link Fundamentals#EMPTY}
     * @implNote Paket görünürlüklüdür ve testlerde doğrudan çağrılır.
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
     * JSON bölümündeki alanın {@code raw} sayısal değerini okur.
     *
     * @param parent  üst JSON düğümü
     * @param section modül adı (ör. {@code summaryDetail})
     * @param field   alan adı (ör. {@code trailingPE})
     * @return {@code raw} değer; sayısal değilse {@code 0.0}
     */
    private double node(JsonNode parent, String section, String field) {
        JsonNode n = parent.path(section).path(field).path("raw");
        return n.isNumber() ? n.asDouble() : 0.0;
    }
}
