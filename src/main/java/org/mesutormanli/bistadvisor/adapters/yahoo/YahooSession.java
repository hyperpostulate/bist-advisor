package org.mesutormanli.bistadvisor.adapters.yahoo;

import org.springframework.stereotype.Component;

import java.net.http.HttpResponse;

/**
 * Yahoo Finance yetkilendirme el sıkışmasını (oturum çerezi + crumb) yöneten oturum nesnesi.
 * <p>
 * Crumb, {@link #crumb()} ilk çağrısında tembel biçimde (lazy) alınır: önce oturum çerezi
 * elde edilir, ardından bu çerezle crumb isteği yapılır. Crumb isteği HTTP 200 ve boş
 * olmayan gövde ister; aksi hâlde {@link IllegalStateException} fırlatılır. 401 yanıtı
 * sonrası {@link #invalidate()} ile el sıkışma sıfırlanır ve sonraki çağrı yeni oturum
 * kurar. Metotlar {@code synchronized} olduğundan sınıf thread-safe'tir.
 */
@Component
public class YahooSession {

    private static final String CRUMB_URL = "https://query2.finance.yahoo.com/v1/test/getcrumb";
    private static final String COOKIE_URL = "https://fc.yahoo.com";

    private final RetryingHttpTransport transport;
    private volatile String crumb;
    private volatile String sessionCookie;

    /**
     * Kurar: HTTP erişimi için taşıma katmanını enjekte eder.
     *
     * @param transport denetimli/yeniden denemeli HTTP taşıması
     */
    public YahooSession(RetryingHttpTransport transport) {
        this.transport = transport;
    }

    /**
     * Oturum çerezini ve crumb değerini alarak istek yetkilendirmesini hazırlar ve crumb'ı döndürür.
     * <p>
     * Senkronize çalışır; crumb zaten alınmışsa yeni istek yapılmaz. Crumb isteği
     * HTTP 200 ve boş olmayan gövde ister.
     *
     * @return yetkilendirme için kullanılacak crumb değeri
     * @throws Exception çerez veya crumb isteği ağ hatasıyla sonuçlanırsa
     * @throws IllegalStateException crumb isteği HTTP 200 dönmez veya gövde boşsa
     */
    public synchronized String crumb() throws Exception {
        if (crumb != null) return crumb;
        HttpResponse<String> cr = transport.get(COOKIE_URL, null);
        if (cr == null) throw new IllegalStateException("crumb alinamadi: HTTP 0");
        sessionCookie = cookieString(cr.headers());
        HttpResponse<String> rr = transport.get(CRUMB_URL, sessionCookie);
        if (rr == null || rr.statusCode() != 200 || rr.body().isBlank())
            throw new IllegalStateException("crumb alinamadi: HTTP " + (rr == null ? 0 : rr.statusCode()));
        crumb = rr.body().trim();
        return crumb;
    }

    /**
     * İsteklerde kullanılacak oturum çerezini döndürür.
     *
     * @return çerez dizesi; el sıkışma henüz yapılmadıysa {@code null}
     */
    public synchronized String cookie() {
        return sessionCookie;
    }

    /**
     * El sıkışmayı sıfırlar; sonraki {@link #crumb()} çağrısı yeni oturum kurar.
     * <p>
     * 401 (yetkisiz) yanıtı sonrası çağrılır.
     */
    public synchronized void invalidate() {
        crumb = null;
        sessionCookie = null;
    }

    /**
     * Yanıt başlıklarındaki {@code Set-Cookie} değerlerini birleştirip isteklerde kullanılacak
     * çerez dizesini oluşturur ({@code "ad=deger; ad2=deger2"} biçimi).
     *
     * @param headers HTTP yanıtı başlıkları
     * @return birleştirilmiş çerez dizesi; uygun başlık yoksa boş dize
     */
    private static String cookieString(java.net.http.HttpHeaders headers) {
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
}