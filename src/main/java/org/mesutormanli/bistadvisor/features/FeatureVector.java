package org.mesutormanli.bistadvisor.features;

import org.mesutormanli.bistadvisor.data.YahooClient.Fundamentals;

/**
 * 11 boyutlu normalleştirilmiş öznitelik vektörü.
 * <p>
 * Teknik göstergeler (RSI, SMA oranları, MACD, volatilite, hacim oranı) ile
 * temel verileri (F/K, PD/DD, temettü verimi, büyüme, ROE) birleştirir.
 * Tüm değerler {@link #normalize()} ile {@code [0, 1]} aralığına ölçeklenir.
 *
 * @param rsi           Göreceli Güç Endeksi (14 günlük)
 * @param sma20Ratio    20 günlük SMA'ya göre fiyat oranı (close/SMA - 1)
 * @param sma50Ratio    50 günlük SMA'ya göre fiyat oranı
 * @param macd          MACD (12/26 EMA farkı), fiyata bölünerek normalize edilmiş
 * @param volatility    günlük getirilerin standart sapması (20 gün)
 * @param volumeRatio   son hacmin 20 günlük ortalama hacme oranı
 * @param fk            Fiyat/Kazanç oranı
 * @param pdDd          PD/DD oranı
 * @param dividendYield temettü verimi
 * @param profitGrowth  kâr büyüme oranı
 * @param roe           özkaynak karlılığı (Return on Equity)
 */
public record FeatureVector(
        double rsi, double sma20Ratio, double sma50Ratio, double macd,
        double volatility, double volumeRatio, double fk, double pdDd,
        double dividendYield, double profitGrowth, double roe
) {

    /**
     * 11 özniteliğin isimlerini döndürür (model eğitimi için sütun adları).
     *
     * @return öznitelik isimleri dizisi
     */
    public static String[] featureNames() {
        return new String[]{
                "rsi", "sma20Ratio", "sma50Ratio", "macd", "volatility",
                "volumeRatio", "fk", "pdDd", "dividendYield", "profitGrowth", "roe"
        };
    }

    /**
     * Öznitelik vektörünü {@code double[]} dizisine dönüştürür.
     *
     * @return 11 elemanlı dizi
     */
    public double[] toArray() {
        return new double[]{rsi, sma20Ratio, sma50Ratio, macd, volatility,
                volumeRatio, fk, pdDd, dividendYield, profitGrowth, roe};
    }

    /**
     * Fiyat çubukları ve temel verilerden bir {@code FeatureVector} oluşturur.
     * Tüm teknik göstergeleri hesaplar ve temel verilerle birleştirir.
     *
     * @param fundamentals temel veriler (null olabilir)
     * @param bars         fiyat çubukları serisi
     * @return öznitelik vektörü
     */
    public static FeatureVector fromBars(Fundamentals fundamentals,
                                         java.util.List<TechnicalFeatures.Bar> bars) {
        if (bars == null || bars.isEmpty()) return new FeatureVector(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        double macdRaw = TechnicalFeatures.macd(bars);
        double curClose = bars.getLast().close();
        double macdNorm = curClose != 0 ? macdRaw / curClose : 0;
        if (fundamentals != null) {
            return new FeatureVector(
                    TechnicalFeatures.rsi(bars, 14),
                    TechnicalFeatures.smaRatio(bars, 20),
                    TechnicalFeatures.smaRatio(bars, 50),
                    macdNorm,
                    TechnicalFeatures.volatility(bars, 20),
                    TechnicalFeatures.volumeRatio(bars, 20),
                    fundamentals.fk(), fundamentals.pdDd(), fundamentals.dividendYield(),
                    fundamentals.profitGrowth(), fundamentals.roe()
            );
        }
        return new FeatureVector(
                TechnicalFeatures.rsi(bars, 14),
                TechnicalFeatures.smaRatio(bars, 20),
                TechnicalFeatures.smaRatio(bars, 50),
                macdNorm,
                TechnicalFeatures.volatility(bars, 20),
                TechnicalFeatures.volumeRatio(bars, 20),
                0, 0, 0, 0, 0
        );
    }

    /**
     * Tüm öznitelik değerlerini {@code [0, 1]} aralığına ölçekleyerek
     * yeni bir {@code FeatureVector} döndürür (orijinal nesne değişmez).
     *
     * @return normalleştirilmiş vektör
     */
    public FeatureVector normalize() {
        return new FeatureVector(
                clamp(rsi / 100.0),
                clamp((sma20Ratio + 1) / 2.0),
                clamp((sma50Ratio + 1) / 2.0),
                clamp(macd * 10.0 + 0.5),
                clamp(volatility * 50.0),
                clamp(volumeRatio / 3.0),
                clamp(fk / 50.0),
                clamp(pdDd / 10.0),
                clamp(dividendYield / 10.0),
                clamp((profitGrowth * 100.0 + 50.0) / 100.0),
                clamp((roe * 100.0 + 50.0) / 100.0)
        );
    }

    /**
     * Bir değeri {@code [0, 1]} aralığına sıkıştırır.
     *
     * @param v girdi değeri
     * @return {@code max(0.0, min(1.0, v))}
     */
    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
