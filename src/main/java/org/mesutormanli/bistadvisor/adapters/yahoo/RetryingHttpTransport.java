package org.mesutormanli.bistadvisor.adapters.yahoo;

import org.mesutormanli.bistadvisor.config.AppConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Yahoo Finance HTTP isteklerini denetimli (throttled) ve yeniden denemeli yürüten taşıma katmanı.
 * <p>
 * Tek sorumluluğu güvenilir HTTP GET'tir: istekler arasına {@code bist.scrape.delay-ms}
 * kadar gecikme konur, istek zaman aşımı {@code bist.scrape.timeout-ms} ile belirlenir ve
 * 429/5xx yanıtlarında 500 ms ile başlayan, her denemede ikiye katlanan üstel geri çekilme
 * ile en fazla 3 deneme yapılır. 401 yanıtı olduğu gibi döndürülür; oturum/crumb
 * sıfırlaması çağıran katmanın (bkz. {@link YahooSession}) sorumluluğudur. Diğer durum
 * kodları da aynen döndürülür; denemeler tükenirse {@code null} döner.
 */
@Component
public class RetryingHttpTransport {

    /** Tarayıcı taklidi User-Agent. */
    static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final int MAX_RETRIES = 3;

    private final HttpClient http;
    private final long requestDelayMs;
    private final long requestTimeoutMs;

    /**
     * Kurar: gecikme ve zaman aşımını uygulama yapılandırmasından alır (Spring bileşeni).
     *
     * @param appConfig istek gecikmesi ve zaman aşımını içeren yapılandırma
     */
    @Autowired
    public RetryingHttpTransport(AppConfig appConfig) {
        this(appConfig.scrapeDelayMs(), appConfig.scrapeTimeoutMs());
    }

    /**
     * Kurar ve bağlantı zaman aşımı 15 saniye olan, yönlendirmeleri izleyen HTTP istemcisini hazırlar.
     *
     * @param requestDelayMs   istekler arası gecikme (ms; ≤ 0 ise bekleme yapılmaz)
     * @param requestTimeoutMs istek zaman aşımı (ms)
     */
    public RetryingHttpTransport(long requestDelayMs, long requestTimeoutMs) {
        this.requestDelayMs = requestDelayMs;
        this.requestTimeoutMs = requestTimeoutMs;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * HTTP GET isteğini en fazla 3 denemede gönderir.
     * <p>
     * 200 yanıtı olduğu gibi döner. 401 yanıtı çağıran katmanın oturumu yenilemesi için
     * olduğu gibi döndürülür. 429 ve 5xx yanıtlarında 500 ms ile başlayan ve her denemede
     * ikiye katlanan üstel geri çekilme uygulanır; diğer durum kodları aynen döndürülür.
     * Denemeler tükenirse {@code null} döner.
     *
     * @param url    istek URL'si
     * @param cookie isteğe eklenecek çerez dizesi ({@code null} = çerez eklenmez)
     * @return HTTP yanıtı; tüm denemeler sonuçsuz kalırsa {@code null}
     * @throws Exception istek gönderilirken ağ veya kesinti hatası olursa
     */
    public HttpResponse<String> get(String url, String cookie) throws Exception {
        long backoff = 500;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            throttle();
            var builder = HttpRequest.newBuilder()
                    .uri(URI.create(url)).timeout(Duration.ofMillis(requestTimeoutMs))
                    .header("User-Agent", UA).header("Accept", "application/json");
            if (cookie != null) builder.header("Cookie", cookie);
            HttpResponse<String> res = http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
            int code = res.statusCode();
            if (code == 200 || code == 401) return res;
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
     * İstekler arasına yapılandırılan gecikme kadar bekler.
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
}