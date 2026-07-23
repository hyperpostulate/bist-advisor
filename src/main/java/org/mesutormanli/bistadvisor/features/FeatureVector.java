package org.mesutormanli.bistadvisor.features;

import org.mesutormanli.bistadvisor.data.YahooClient.Fundamentals;

/** 11 boyutlu ozellik vektoru (6 teknik + 5 temel). ML modeline beslenir. */
public final class FeatureVector {
    /** RSI (14 gunluk). */
    public double rsi;
    /** Son fiyat / SMA-20 - 1 (fiyat momentumu). */
    public double sma20Ratio;
    /** Son fiyat / SMA-50 - 1 (uzun vadeli momentum). */
    public double sma50Ratio;
    /** MACD (EMA12 - EMA26). */
    public double macd;
    /** 20 gunluk getiri standart sapmasi (volatilite). */
    public double volatility;
    /** Son gun hacmi / 20 gunluk ortalama hacim. */
    public double volumeRatio;
    /** F/K (trailing, yoksa forward). */
    public double fk;
    /** PD/DD. */
    public double pdDd;
    /** Temettu verimi. */
    public double dividendYield;
    /** Kar buyumesi (yoksa gelir buyumesine duser). */
    public double profitGrowth;
    /** Ozkaynak karliligi (ROE). */
    public double roe;

    public static String[] featureNames() {
        return new String[]{
                "rsi", "sma20Ratio", "sma50Ratio", "macd", "volatility",
                "volumeRatio", "fk", "pdDd", "dividendYield", "profitGrowth", "roe"
        };
    }

    public double[] toArray() {
        return new double[]{rsi, sma20Ratio, sma50Ratio, macd, volatility,
                volumeRatio, fk, pdDd, dividendYield, profitGrowth, roe};
    }

    public static FeatureVector fromBars(Fundamentals fundamentals,
                                         java.util.List<TechnicalFeatures.Bar> bars) {
        FeatureVector fv = new FeatureVector();
        if (bars == null || bars.isEmpty()) return fv;
        fv.rsi = TechnicalFeatures.rsi(bars, 14);
        fv.sma20Ratio = TechnicalFeatures.smaRatio(bars, 20);
        fv.sma50Ratio = TechnicalFeatures.smaRatio(bars, 50);
        fv.macd = TechnicalFeatures.macd(bars);
        fv.volatility = TechnicalFeatures.volatility(bars, 20);
        fv.volumeRatio = TechnicalFeatures.volumeRatio(bars, 20);
        if (fundamentals != null) {
            fv.fk = fundamentals.fk();
            fv.pdDd = fundamentals.pdDd();
            fv.dividendYield = fundamentals.dividendYield();
            fv.profitGrowth = fundamentals.profitGrowth();
            fv.roe = fundamentals.roe();
        }
        return fv;
    }

    /** Min-max normalize et (0..1). SVM/KNN icin onemli. */
    public void normalize() {
        rsi = clamp(rsi / 100.0);
        sma20Ratio = clamp((sma20Ratio + 1) / 2.0);
        sma50Ratio = clamp((sma50Ratio + 1) / 2.0);
        macd = clamp(macd / 30.0 + 0.5);
        volatility = clamp(volatility * 50.0);
        volumeRatio = clamp(volumeRatio / 3.0);
        fk = clamp(fk / 50.0);
        pdDd = clamp(pdDd / 10.0);
        dividendYield = clamp(dividendYield / 10.0);
        profitGrowth = clamp((profitGrowth * 100.0 + 50.0) / 100.0);
        roe = clamp((roe * 100.0 + 50.0) / 100.0);
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
