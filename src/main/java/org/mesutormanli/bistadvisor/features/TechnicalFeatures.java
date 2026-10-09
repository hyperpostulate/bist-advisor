package org.mesutormanli.bistadvisor.features;

import java.util.ArrayList;
import java.util.List;

/**
 * Fiyat çubuklarından teknik göstergeler hesaplayan yardımcı sınıf.
 * <p>
 * RSI, SMA, SMA oranı, MACD, EMA, oynaklık (günlük getirilerin standart sapması) ve hacim
 * oranı gibi göstergeler {@link Bar} serileri üzerinden hesaplanır.
 */
public final class TechnicalFeatures {

    /**
     * Kurar; yardımcı sınıfın örneklenmesini engellemek için gizli tutulur.
     */
    private TechnicalFeatures() {}

    /**
     * Günlük fiyat verisini temsil eden değiştirilemez kayıt: {@code date} barın tarihi,
     * {@code close} kapanış fiyatı, {@code volume} işlem hacmidir.
     *
     * @param date   bar tarihi ({@code YYYY-AA-GG})
     * @param close  kapanış fiyatı
     * @param volume işlem hacmi
     */
    public record Bar(String date, double close, double volume) {}

    /**
     * CSV satırlarını {@link Bar} nesnelerine dönüştürür.
     * <p>
     * Hem 3+ kolonlu {@code tarih,kapanış,hacim} hem de 6 kolonlu OHLCV
     * {@code tarih,aç,yüksek,düşük,kapanış,hacim} biçimi desteklenir: 5+ kolonda kapanış
     * 5. alandan (indeks 4), 6+ kolonda hacim 6. alandan (indeks 5) okunur.
     *
     * @param csvLines ayrıştırılacak CSV satırları
     * @return oluşturulan {@link Bar} listesi
     * @implNote Sayısal olmayan alan içeren satırlar sessizce atlanır.
     */
    public static List<Bar> toBars(List<String> csvLines) {
        List<Bar> bars = new ArrayList<>();
        for (String line : csvLines) {
            String[] p = line.split(",");
            if (p.length < 3) continue;
            try {
                double close = p.length >= 5 ? Double.parseDouble(p[4]) : Double.parseDouble(p[1]);
                double vol = p.length >= 6 ? Double.parseDouble(p[5]) : Double.parseDouble(p[2]);
                bars.add(new Bar(p[0], close, vol));
            } catch (NumberFormatException ignored) {}
        }
        return bars;
    }

    /**
     * Göreceli Güç Endeksi'ni (RSI) hesaplar.
     * <p>
     * Son {@code period} fiyat değişimine göre Wilder benzeri ortalama kazanç ve kayıp
     * kullanılır: {@code RSI = 100 − 100 / (1 + RS)}, {@code RS = ortalama kazanç / ortalama kayıp}.
     *
     * @param bars   fiyat çubukları
     * @param period değişim sayısı (tipik olarak 14 dönemlik RSI)
     * @return RSI değeri; veri yetersizse nötr 50, kayıp yoksa 100
     */
    public static double rsi(List<Bar> bars, int period) {
        if (bars.size() <= period) return 50.0;
        double gain = 0, loss = 0;
        for (int i = bars.size() - period; i < bars.size(); i++) {
            double diff = bars.get(i).close() - bars.get(i - 1).close();
            if (diff >= 0) gain += diff;
            else loss -= diff;
        }
        gain /= period;
        loss /= period;
        if (loss == 0) return 100.0;
        double rs = gain / loss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    /**
     * Son {@code period} kapanış fiyatının ortalamasını (SMA) hesaplar.
     *
     * @param bars   fiyat çubukları
     * @param period ortalama uzunluğu
     * @return basit hareketli ortalama; veri yetersizse son kapanış, liste boşsa 0
     */
    public static double sma(List<Bar> bars, int period) {
        if (bars.size() < period) return bars.isEmpty() ? 0 : bars.getLast().close();
        double sum = 0;
        for (int i = bars.size() - period; i < bars.size(); i++) sum += bars.get(i).close();
        return sum / period;
    }

    /**
     * Fiyatın SMA'ya oranını hesaplar: {@code kapanış / SMA − 1}.
     *
     * @param bars   fiyat çubukları
     * @param period SMA uzunluğu (20 veya 50)
     * @return SMA oranı; SMA 0 ise 0
     */
    public static double smaRatio(List<Bar> bars, int period) {
        double sma = sma(bars, period);
        if (sma == 0) return 0;
        return bars.getLast().close() / sma - 1.0;
    }

    /**
     * MACD değerini hesaplar: {@code EMA12 − EMA26}.
     *
     * @param bars fiyat çubukları
     * @return 12 ve 26 dönemlik EMA'ların farkı
     */
    public static double macd(List<Bar> bars) {
        double ema12 = ema(bars, 12);
        double ema26 = ema(bars, 26);
        return ema12 - ema26;
    }

    /**
     * Üstel hareketli ortalamayı (EMA) hesaplar.
     * <p>
     * İlk {@code period} değerin SMA'sıyla tohumlanır, ardından
     * {@code k = 2 / (period + 1)} katsayısıyla üssel yumuşatma sürdürülür.
     *
     * @param bars   fiyat çubukları
     * @param period EMA dönemi
     * @return EMA değeri; veri yetersizse son kapanış, liste boşsa 0
     */
    private static double ema(List<Bar> bars, int period) {
        if (bars.size() < period) return bars.isEmpty() ? 0 : bars.getLast().close();
        double k = 2.0 / (period + 1);
        double ema = sma(bars.subList(0, period), period);
        for (int i = period; i < bars.size(); i++) {
            ema = bars.get(i).close() * k + ema * (1 - k);
        }
        return ema;
    }

    /**
     * Günlük getirilerin standart sapmasını (oynaklık) hesaplar.
     * <p>
     * En fazla son {@code period} günlük getiri kullanılır; varyans paydası gözlem
     * sayısı (n)dir.
     *
     * @param bars   fiyat çubukları
     * @param period azami getiri penceresi (tipik olarak 20)
     * @return oynaklık (standart sapma); iki bardan az veri varsa 0
     */
    public static double volatility(List<Bar> bars, int period) {
        if (bars.size() < 2) return 0;
        int n = Math.min(period, bars.size() - 1);
        List<Double> rets = new ArrayList<>();
        for (int i = bars.size() - n; i < bars.size(); i++) {
            double r = (bars.get(i).close() - bars.get(i - 1).close()) / bars.get(i - 1).close();
            rets.add(r);
        }
        double mean = rets.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double var = rets.stream().mapToDouble(d -> (d - mean) * (d - mean)).sum() / rets.size();
        return Math.sqrt(var);
    }

    /**
     * Son barın hacminin {@code period} günlük ortalama hacme oranını hesaplar.
     *
     * @param bars   fiyat çubukları
     * @param period ortalama penceresi (tipik olarak 20)
     * @return hacim oranı; veri yetersizse veya ortalama 0 ise 1.0
     */
    public static double volumeRatio(List<Bar> bars, int period) {
        if (bars.size() < period) return 1.0;
        double sum = 0;
        for (int i = bars.size() - period; i < bars.size(); i++) sum += bars.get(i).volume();
        double avg = sum / period;
        if (avg == 0) return 1.0;
        return bars.getLast().volume() / avg;
    }
}
