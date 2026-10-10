package org.mesutormanli.bistadvisor.adapters.yahoo;

import org.mesutormanli.bistadvisor.domain.market.Bar;
import org.mesutormanli.bistadvisor.domain.market.Fundamentals;
import org.mesutormanli.bistadvisor.ports.MarketDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Yahoo Finance REST uçlarından günlük fiyat ve temel gösterge verilerini çeken
 * {@link MarketDataSource} adaptörü.
 * <p>
 * Günlük fiyatlar v8 chart ucundan ({@code range=1y&interval=1d}), temel göstergeler ise v10
 * quoteSummary ucunun {@code summaryDetail}, {@code defaultKeyStatistics}, {@code financialData} ve
 * {@code price} modülleriyle alınır. Fiyat istekleri crumb'sız; temel gösterge istekleri
 * {@link YahooSession} el sıkışmasıyla yetkilendirilir ({@code &crumb=} eki). 401 yanıtlarında
 * oturum {@code invalidate()} ile sıfırlanır ve yetkilendirme tazelenerek yeniden denenir.
 * HTTP yürütmeleri {@link RetryingHttpTransport}, ayrıştırma {@link YahooJsonParser} ile
 * yapılır; temel göstergeler gün bazlı {@link FundamentalsDayCache} içinde tutulur.
 */
@Component
public class YahooMarketDataSource implements MarketDataSource {
    private static final Logger log = LoggerFactory.getLogger(YahooMarketDataSource.class);
    private static final String PRICE_URL = "https://query2.finance.yahoo.com/v8/finance/chart/%s?range=1y&interval=1d";
    private static final String FUND_URL = "https://query2.finance.yahoo.com/v10/finance/quoteSummary/%s?modules=summaryDetail,defaultKeyStatistics,financialData,price";

    /** 401 sonrası yetkilendirme tazeleme deneme sayısı. */
    private static final int MAX_AUTH_RETRIES = 3;

    private final RetryingHttpTransport transport;
    private final YahooSession session;
    private final FundamentalsDayCache fundCache;

    /**
     * Kurar: HTTP taşıması, yetkilendirme oturumu ve temel gösterge önbelleğini enjekte eder.
     *
     * @param transport denetimli/yeniden denemeli HTTP taşıması
     * @param session   cookie + crumb el sıkışmasını yöneten oturum
     * @param fundCache gün bazlı temel gösterge önbelleği
     */
    public YahooMarketDataSource(RetryingHttpTransport transport, YahooSession session,
                                 FundamentalsDayCache fundCache) {
        this.transport = transport;
        this.session = session;
        this.fundCache = fundCache;
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
    @Override
    public List<Bar> fetchPrices(String symbol) {
        String url = String.format(PRICE_URL, yahooSymbol(symbol));
        try {
            HttpResponse<String> res = transport.get(url, null);
            if (res == null || res.statusCode() != 200) return List.of();
            return YahooJsonParser.parsePrices(res.body());
        } catch (Exception e) {
            log.warn("fiyat hatasi {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    /**
     * Sembol için temel göstergeleri (F/K, PD/DD, temettü verimi, kâr büyümesi, ROE) v10
     * quoteSummary ucundan çeker.
     * <p>
     * Sonuçlar gün bazlı bellek içi önbellekte tutulur; aynı gün içindeki tekrarlı çağrılar
     * ağa çıkmaz. 401 yanıtında oturum sıfırlanır, yetkilendirme tazelenir ve en fazla
     * {@value #MAX_AUTH_RETRIES} kez yeniden denenir.
     *
     * @param symbol hisse sembolü
     * @return temel göstergeler; hata durumunda {@link Fundamentals#EMPTY}
     */
    @Override
    public Fundamentals fetchFundamentals(String symbol) {
        String ysym = yahooSymbol(symbol);
        Fundamentals hit = fundCache.getIfFresh(ysym);
        if (hit != null) return hit;
        try {
            for (int attempt = 0; attempt < MAX_AUTH_RETRIES; attempt++) {
                String crumb = session.crumb();
                String url = String.format(FUND_URL, ysym)
                        + "&crumb=" + URLEncoder.encode(crumb, StandardCharsets.UTF_8);
                HttpResponse<String> res = transport.get(url, session.cookie());
                if (res != null && res.statusCode() == 401) {
                    session.invalidate();
                    continue;
                }
                if (res == null || res.statusCode() != 200) return Fundamentals.EMPTY;
                Fundamentals f = YahooJsonParser.parseFundamentals(res.body());
                fundCache.put(ysym, f);
                return f;
            }
            return Fundamentals.EMPTY;
        } catch (Exception e) {
            log.warn("temel veri hatasi {}: {}", symbol, e.getMessage());
            return Fundamentals.EMPTY;
        }
    }
}