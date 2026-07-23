package org.mesutormanli.bistadvisor.features;

import java.util.ArrayList;
import java.util.List;

/** Fiyat serisinden RSI, SMA, MACD, volatilite, hacim orani gibi teknik gostergeleri hesaplar. */
public final class TechnicalFeatures {

    private TechnicalFeatures() {}

    /** Tek bir gunluk fiyat kaydi: tarih, kapanis, hacim. */
    public record Bar(String date, double close, double volume) {}

    /** CSV satirlarini (date,close,vol veya eski format date,open,high,low,close,vol) Bar listesine cevirir. */
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

    public static double sma(List<Bar> bars, int period) {
        if (bars.size() < period) return bars.isEmpty() ? 0 : bars.getLast().close();
        double sum = 0;
        for (int i = bars.size() - period; i < bars.size(); i++) sum += bars.get(i).close();
        return sum / period;
    }

    /** Son fiyat / SMA(period) - 1 (momentum). */
    public static double smaRatio(List<Bar> bars, int period) {
        double sma = sma(bars, period);
        if (sma == 0) return 0;
        return bars.getLast().close() / sma - 1.0;
    }

    public static double macd(List<Bar> bars) {
        double ema12 = ema(bars, 12);
        double ema26 = ema(bars, 26);
        return ema12 - ema26;
    }

    private static double ema(List<Bar> bars, int period) {
        if (bars.size() < period) return bars.isEmpty() ? 0 : bars.getLast().close();
        double k = 2.0 / (period + 1);
        double ema = sma(bars.subList(0, period), period);
        for (int i = period; i < bars.size(); i++) {
            ema = bars.get(i).close() * k + ema * (1 - k);
        }
        return ema;
    }

    /** Son period gunun gunluk getiri standart sapmasi (volatilite). */
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

    /** Son gunun hacim ortalamasina gore orani. */
    public static double volumeRatio(List<Bar> bars, int period) {
        if (bars.size() < period) return 1.0;
        double sum = 0;
        for (int i = bars.size() - period; i < bars.size(); i++) sum += bars.get(i).volume();
        double avg = sum / period;
        if (avg == 0) return 1.0;
        return bars.getLast().volume() / avg;
    }
}
