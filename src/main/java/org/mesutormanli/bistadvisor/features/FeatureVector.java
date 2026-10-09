package org.mesutormanli.bistadvisor.features;

import org.mesutormanli.bistadvisor.config.AnalysisType;
import org.mesutormanli.bistadvisor.data.YahooClient.Fundamentals;

/**
 * Teknik göstergelerle temel göstergeleri birlikte taşıyan 11 boyutlu, değiştirilemez öznitelik vektörü.
 * <p>
 * Bileşen sırası {@link #featureNames()} ve {@link #toArray()} düzeniyle, yani modelin beklediği
 * FeatureFrame/SMILE matris düzeniyle aynıdır. Ham değerler farklı ölçeklerdedir; {@link #normalize()}
 * her alanı ayrıca [0, 1] aralığına ölçekleyip kırpar ve yeni bir örnek döndürür.
 *
 * @param rsi           RSI değeri (14 dönemlik; ham hâlde [0, 100] dışına taşabilir)
 * @param sma20Ratio    fiyatın 20 günlük SMA'ya sapması ({@code kapanış/SMA − 1})
 * @param sma50Ratio    fiyatın 50 günlük SMA'ya sapması ({@code kapanış/SMA − 1})
 * @param macd          normalize MACD ({@code (EMA12 − EMA26) / son kapanış})
 * @param volatility    20 günlük getirilerin standart sapması (oynaklık)
 * @param volumeRatio   son hacmin 20 günlük ortalama hacme oranı
 * @param fk            F/K oranı
 * @param pdDd          PD/DD oranı
 * @param dividendYield temettü verimi
 * @param profitGrowth  kâr büyümesi
 * @param roe           özkaynak kârlılığı (ROE)
 */
public record FeatureVector(
        double rsi, double sma20Ratio, double sma50Ratio, double macd,
        double volatility, double volumeRatio, double fk, double pdDd,
        double dividendYield, double profitGrowth, double roe
) {

    /** Teknik gösterge kolon adları; {@link #toArray()} düzenindeki ilk 6 sütunun adları. */
    private static final String[] TECHNICAL_NAMES = {
            "rsi", "sma20Ratio", "sma50Ratio", "macd", "volatility", "volumeRatio"
    };

    /** Temel gösterge kolon adları; {@link #toArray()} düzenindeki son 5 sütunun adları. */
    private static final String[] FUNDAMENTAL_NAMES = {
            "fk", "pdDd", "dividendYield", "profitGrowth", "roe"
    };

    /**
     * Modelin beklediği tüm (11) öznitelik kolon adlarını döndürür.
     *
     * @return FeatureFrame/SMILE matris düzeninde 11 kolon adı
     * @implNote {@code featureNames(AnalysisType.TECHNICAL_FUNDAMENTAL)} çağrısına eşdeğerdir.
     */
    public static String[] featureNames() {
        return featureNames(AnalysisType.TECHNICAL_FUNDAMENTAL);
    }

    /**
     * Analiz tipinin kullandığı öznitelik kolon adlarını döndürür.
     *
     * <p>Sütun düzeni sabittir: önce teknik göstergeler (varsa), sonra temel göstergeler
     * (varsa). Eğitim matrisi ve tahmin vektörü bu düzen üzerinden kurulduğu için
     * {@link #toArray(org.mesutormanli.bistadvisor.config.AnalysisType)} ile birebir
     * eşleşir.</p>
     *
     * @param type analiz tipi ({@code YALNIZCA_TEKNIK} 6 sütun, {@code YALNIZCA_TEMEL}
     *             5 sütun, {@code TEKNIK_TEMEL} 11 sütun)
     * @return FeatureFrame/SMILE matris düzeninde kolon adları
     */
    public static String[] featureNames(AnalysisType type) {
        if (type.usesTechnical() && type.usesFundamental()) {
            String[] all = new String[TECHNICAL_NAMES.length + FUNDAMENTAL_NAMES.length];
            System.arraycopy(TECHNICAL_NAMES, 0, all, 0, TECHNICAL_NAMES.length);
            System.arraycopy(FUNDAMENTAL_NAMES, 0, all, TECHNICAL_NAMES.length, FUNDAMENTAL_NAMES.length);
            return all;
        }
        return (type.usesTechnical() ? TECHNICAL_NAMES : FUNDAMENTAL_NAMES).clone();
    }

    /**
     * Analiz tipinin kullandığı öznitelik sayısını (matris sütun genişliğini) döndürür.
     *
     * @param type analiz tipi
     * @return {@code YALNIZCA_TEKNIK} için 6, {@code YALNIZCA_TEMEL} için 5,
     *         {@code TEKNIK_TEMEL} için 11
     */
    public static int dimension(AnalysisType type) {
        return (type.usesTechnical() ? TECHNICAL_NAMES.length : 0)
                + (type.usesFundamental() ? FUNDAMENTAL_NAMES.length : 0);
    }

    /**
     * Vektörü FeatureFrame/SMILE matris düzeninde (tüm 11 bileşenle) {@code double[]} dizisine
     * dönüştürür.
     *
     * @return bileşen sırası {@link #featureNames()} ile eşleşen 11 elemanlı dizi
     * @implNote {@code toArray(AnalysisType.TECHNICAL_FUNDAMENTAL)} çağrısına eşdeğerdir.
     */
    public double[] toArray() {
        return toArray(AnalysisType.TECHNICAL_FUNDAMENTAL);
    }

    /**
     * Vektörü analiz tipinin kullandığı bileşenlerle FeatureFrame/SMILE matris düzeninde
     * {@code double[]} dizisine dönüştürür.
     *
     * <p>Yalnızca seçili metrikler yazılır: önce teknik göstergeler (kullanılıyorsa),
     * sonra temel göstergeler (kullanılıyorsa). Böylece eğitim matrisi ve tahmin vektörü
     * aynı analiz tipiyle aynı genişliğe ve düzene sahip olur.</p>
     *
     * @param type analiz tipi
     * @return bileşen sırası {@link #featureNames(AnalysisType)} ile eşleşen dizi
     *         ({@code dimension(type)} elemanlı)
     */
    public double[] toArray(AnalysisType type) {
        double[] technical = {rsi, sma20Ratio, sma50Ratio, macd, volatility, volumeRatio};
        double[] fundamental = {fk, pdDd, dividendYield, profitGrowth, roe};
        boolean useTech = type.usesTechnical();
        boolean useFund = type.usesFundamental();
        if (useTech && useFund) {
            double[] all = new double[technical.length + fundamental.length];
            System.arraycopy(technical, 0, all, 0, technical.length);
            System.arraycopy(fundamental, 0, all, technical.length, fundamental.length);
            return all;
        }
        return (useTech ? technical : fundamental).clone();
    }

    /**
     * Fiyat çubuklarından ve temel göstergelerden öznitelik vektörü üretir.
     * <p>
     * Teknik taraf 14 dönemlik RSI, 20 ve 50 günlük SMA oranları, son kapanışa bölünerek
     * normalize edilmiş MACD ({@code (EMA12 − EMA26) / son kapanış}), 20 günlük oynaklık ve
     * 20 günlük hacim oranından oluşur; temel taraf F/K, PD/DD, temettü verimi, kâr büyümesi ve
     * özkaynak kârlılığını (ROE) taşır.
     *
     * @param fundamentals temel göstergeler ({@code null} olabilir)
     * @param bars         fiyat çubukları serisi
     * @return hesaplanan öznitelik vektörü
     * @implNote {@code fundamentals} {@code null} ise temel alanlar 0 olur; {@code bars}
     *           boş veya {@code null} ise tüm bileşenleri 0 olan vektör döner.
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
     * Her alanı [0, 1] aralığına ölçekleyip kırparak yeni bir vektör döndürür.
     * <p>
     * Ölçekleme formülleri: RSI → {@code rsi/100}, SMA oranları → {@code (oran+1)/2},
     * MACD → {@code macd*10+0.5}, oynaklık → {@code volatility*50}, hacim oranı → {@code volumeRatio/3},
     * F/K → {@code fk/50}, PD/DD → {@code pdDd/10}, temettü verimi → {@code dividendYield/10},
     * kâr büyümesi → {@code (profitGrowth*100+50)/100}, özkaynak kârlılığı → {@code (roe*100+50)/100}.
     *
     * @return her bileşeni [0, 1] aralığında olan yeni {@code FeatureVector}
     * @implNote Ham RSI [0, 100] aralığının dışına taşabilse de dönüş değeri daima
     *           [0, 1] aralığındadır; kayıt değiştirilemez olduğu için özgün nesne korunur.
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
     * Değeri [0, 1] aralığına kırpar.
     *
     * @param v kırpılacak ham değer
     * @return [0, 1] aralığına indirgenmiş değer ({@code max(0.0, min(1.0, v))})
     */
    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
