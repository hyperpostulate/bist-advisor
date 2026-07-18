package org.mesutormanli.bistadvisor.features;

import java.util.Map;

/**
 * Bir hisse icin teknik + temel ozellik vektoru. ML modeline beslenir.
 */
public final class FeatureVector {
    public double rsi;
    public double sma20Ratio;
    public double sma50Ratio;
    public double macd;
    public double volatility;
    public double volumeRatio;
    public double fk;
    public double pdDd;
    public double dividendYield;
    public double profitGrowth;
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

    public static FeatureVector fromBars(Map<String, Double> fundamentals,
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
            fv.fk = fundamentals.getOrDefault("fk", 0.0);
            fv.pdDd = fundamentals.getOrDefault("pdDd", 0.0);
            fv.dividendYield = fundamentals.getOrDefault("dividendYield", 0.0);
            fv.profitGrowth = fundamentals.getOrDefault("profitGrowth", 0.0);
            fv.roe = fundamentals.getOrDefault("roe", 0.0);
        }
        return fv;
    }

    /** Min-max normalize et (0..1). SVM/KNN icin onemli. */
    public void normalize() {
        // RSI 0..100 zaten sinirli
        rsi = clamp(rsi / 100.0);
        // oranlari -1..1 sinirla
        sma20Ratio = clamp((sma20Ratio + 1) / 2.0);
        sma50Ratio = clamp((sma50Ratio + 1) / 2.0);
        macd = clamp(macd / 10.0 + 0.5); // kabaca
        volatility = clamp(volatility * 20.0); // gunluk %5 vol -> 1.0
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
