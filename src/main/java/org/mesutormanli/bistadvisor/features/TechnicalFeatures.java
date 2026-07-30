package org.mesutormanli.bistadvisor.features;

import java.util.ArrayList;
import java.util.List;

/**
 * Teknik analiz göstergelerini hesaplayan yardımcı sınıf.
 * <p>
 * RSI, SMA, SMA oranı, MACD, üstel hareketli ortalama (EMA), volatilite
 * ve hacim oranı gibi yaygın teknik göstergeleri fiyat çubukları üzerinden
 * hesaplar.
 */
public final class TechnicalFeatures {

    private TechnicalFeatures() {}

    /**
     * Bir günlük fiyat verisini temsil eden kayıt.
     *
     * @param date   tarih ({@code YYYY-MM-DD})
     * @param close  kapanış fiyatı
     * @param volume işlem hacmi
     */
    public record Bar(String date, double close, double volume) {}

    /**
     * CSV satırlarını {@link Bar} nesnelerine dönüştürür.
     * {@code tarih,open,high,low,close,volume} veya {@code tarih,close,volume}
     * formatını destekler.
     *
     * @param csvLines CSV satırları
     * @return {@link Bar} listesi
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
     * RSI = 100 - (100 / (1 + RS)), RS = ortalama kazanç / ortalama kayıp.
     *
     * @param bars   fiyat çubukları
     * @param period dönem (genellikle 14)
     * @return 0-100 arası RSI değeri, yetersiz veride 50
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
     * Basit hareketli ortalamayı (SMA) hesaplar.
     *
     * @param bars   fiyat çubukları
     * @param period dönem uzunluğu
     * @return SMA değeri, yetersiz veride son kapanış veya 0
     */
    public static double sma(List<Bar> bars, int period) {
        if (bars.size() < period) return bars.isEmpty() ? 0 : bars.getLast().close();
        double sum = 0;
        for (int i = bars.size() - period; i < bars.size(); i++) sum += bars.get(i).close();
        return sum / period;
    }

    /**
     * Fiyatın SMA'ya oranını hesaplar: {@code close / sma - 1.0}.
     * Pozitif değer fiyatın SMA'nın üzerinde olduğunu gösterir.
     *
     * @param bars   fiyat çubukları
     * @param period dönem (20 veya 50)
     * @return SMA oranı, SMA=0 ise 0
     */
    public static double smaRatio(List<Bar> bars, int period) {
        double sma = sma(bars, period);
        if (sma == 0) return 0;
        return bars.getLast().close() / sma - 1.0;
    }

    /**
     * MACD (Moving Average Convergence Divergence) değerini hesaplar.
     * MACD = 12 günlük EMA - 26 günlük EMA.
     *
     * @param bars fiyat çubukları
     * @return MACD değeri
     */
    public static double macd(List<Bar> bars) {
        double ema12 = ema(bars, 12);
        double ema26 = ema(bars, 26);
        return ema12 - ema26;
    }

    /**
     * Üstel hareketli ortalamayı (EMA) hesaplar.
     * İlk değer SMA olarak başlatılır, ardından:
     * {@code EMA = fiyat * k + EMA_once * (1 - k)},
     * {@code k = 2 / (period + 1)}.
     *
     * @param bars   fiyat çubukları
     * @param period dönem
     * @return EMA değeri
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
     * Günlük getirilerin standart sapmasını hesaplar (volatilite).
     *
     * @param bars   fiyat çubukları
     * @param period dönem (genellikle 20)
     * @return volatilite (standart sapma), yetersiz veride 0
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
     * Son işlem hacminin periyodik ortalama hacme oranını hesaplar.
     * > 1.0 ise hacim ortalamanın üzerindedir.
     *
     * @param bars   fiyat çubukları
     * @param period dönem (genellikle 20)
     * @return hacim oranı, yetersiz veride 1.0
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
