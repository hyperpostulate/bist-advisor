package org.mesutormanli.bistadvisor.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mesutormanli.bistadvisor.config.AnalysisType;
import org.mesutormanli.bistadvisor.config.ModelType;
import org.mesutormanli.bistadvisor.features.FeatureVector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ModelStrategy} uygulamalarının ortak davranış testleri: üç strateji
 * ({@link ModelType#KNN}, {@link ModelType#SVM}, {@link ModelType#RANDOM_FOREST})
 * ayırt edici sınıfları öğrenmeli, eksik sınıfta istisna fırlatmamalı ve tek
 * sınıflı eğitimde sabit tahmin döndürmelidir. Ayrıca KNN'e özgü komşu çoğunluğu
 * kuralı ve {@link ClassSpace} etiket sıkıştırma/geri açma sözleşmesi sınanır.
 * <p>
 * Veri yapay ve deterministiktir: her örnek {@code DIMS = 11} boyutludur
 * ({@link FeatureVector#featureNames} uzunluğu) ve testler boyutlar arası
 * korelasyondan bağımsız tek boyutlu kümeler kullanır.
 */
class ModelStrategiesTest {

    private static final int DIMS = 11;

    /**
     * Merkezi etrafında çok küçük titreşimli, kompakt bir sınıf bulutu üretir.
     * <p>
     * Her örneğin {@code dims} boyutu da aynı değere ayarlanır ve
     * {@code (i % 5 - 2) * 1e-3} jitter uygulanır; böylece örnekler birbirinden
     * farklı ama sıkı kalır.
     *
     * @param center bulutun merkez değeri
     * @param n      üretilecek örnek sayısı
     * @param dims   öznitelik boyutu (analiz tipinin metrik kümesi)
     * @return boyutları {@code center ± 0,002} civarında titreşen {@code n} örneklik matris
     */
    private static double[][] blob(double center, int n, int dims) {
        double[][] x = new double[n][dims];
        for (int i = 0; i < n; i++) {
            double jitter = (i % 5 - 2) * 1e-3;
            Arrays.fill(x[i], center + jitter);
        }
        return x;
    }

    /**
     * Merkezi etrafında çok küçük titreşimli, tam (11) boyutlu sınıf bulutu üretir.
     *
     * @param center bulutun merkez değeri
     * @param n      üretilecek örnek sayısı
     * @return boyutları {@code center ± 0,002} civarında titreşen {@code n} örneklik matris
     */
    private static double[][] blob(double center, int n) {
        return blob(center, n, DIMS);
    }

    /**
     * Tek bir sorgu örneği üretir.
     *
     * @param value yazılacak değer
     * @param dims  öznitelik boyutu (analiz tipinin metrik kümesi)
     * @return bütün boyutları {@code value} olan tekil özellik vektörü
     */
    private static double[] point(double value, int dims) {
        double[] x = new double[dims];
        Arrays.fill(x, value);
        return x;
    }

    /**
     * Tam (11) boyutlu tek bir sorgu örneği üretir.
     *
     * @param value 11 boyutun tamamına yazılacak değer
     * @return bütün boyutları {@code value} olan tekil özellik vektörü
     */
    private static double[] point(double value) {
        return point(value, DIMS);
    }

    /**
     * İki özellik matrisini satır bazında birleştirir.
     *
     * @param a birinci özellik matrisi
     * @param b ikinci özellik matrisi
     * @return önce {@code a} sonra {@code b} satırlarını içeren birleşik matris
     */
    private static double[][] concat(double[][] a, double[][] b) {
        double[][] out = new double[a.length + b.length][];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /**
     * Tek sınıflı etiket dizisi üretir.
     *
     * @param value yazılacak sınıf etiketi (ör. {@link Labeler#BUY})
     * @param n     etiket sayısı
     * @return tamamı {@code value} olan {@code n} uzunluğunda etiket dizisi
     */
    private static int[] labels(int value, int n) {
        int[] y = new int[n];
        Arrays.fill(y, value);
        return y;
    }

    /**
     * İki etiket dizisini birleştirir.
     *
     * @param a birinci etiket dizisi
     * @param b ikinci etiket dizisi
     * @return önce {@code a} sonra {@code b} elemanlarını içeren birleşik dizi
     */
    private static int[] concat(int[] a, int[] b) {
        int[] out = new int[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /**
     * Üç ayrı buluttan oluşan eğitimde üç sınıfın da doğru öğrenilmesi.
     * <p>
     * Senaryo: merkezleri 0,2 / 0,5 / 0,8 olan üç kompakt bulut sırasıyla AL / SAT /
     * TUT etiketiyle (30'ar örnek) eğitilir. Beklenen davranış: her strateji üç
     * sınıfı da doğru tahmin eder (0,2 → AL, 0,5 → SAT, 0,8 → TUT) ve AL merkezindeki
     * skor 0,5'in üzerinde olur.
     *
     * @param type sınanan model stratejisi
     */
    @ParameterizedTest
    @EnumSource(ModelType.class)
    void ayirtEdiciSiniflariOgrenir(ModelType type) {
        double[][] x = concat(concat(blob(0.2, 30), blob(0.5, 30)), blob(0.8, 30));
        int[] y = concat(concat(labels(Labeler.BUY, 30), labels(Labeler.SELL, 30)), labels(Labeler.HOLD, 30));
        ModelStrategy strategy = ModelStrategyFactory.create(type);
        strategy.train(x, y, AnalysisType.TECHNICAL_FUNDAMENTAL);

        assertEquals(Labeler.BUY, (int) strategy.predict(point(0.2))[0], type.name());
        assertEquals(Labeler.SELL, (int) strategy.predict(point(0.5))[0], type.name());
        assertEquals(Labeler.HOLD, (int) strategy.predict(point(0.8))[0], type.name());
        assertTrue(strategy.predict(point(0.2))[1] > 0.5, type.name() + " skoru 0.5 üstü olmalı");
    }

    /**
     * Eğitim setinde bir sınıf yokken istisna fırlatılmaması.
     * <p>
     * Senaryo: yalnız AL (0,2) ve TUT (0,8) bulutlarıyla eğitim yapılır; SAT sınıfı
     * hiç gözlenmez — bu durum SMILE modellerinde olasılık dizisi boyutu tuzağını
     * tetikler. Beklenen davranış: eğitim ve tahmin istisna fırlatmaz, tahmin yalnız
     * gözlenen sınıflardan birine (AL veya TUT) karşılık gelir.
     *
     * @param type sınanan model stratejisi
     */
    @ParameterizedTest
    @EnumSource(ModelType.class)
    void eksikSinifHatasiFirlatmaz(ModelType type) {
        double[][] x = concat(blob(0.2, 30), blob(0.8, 30));
        int[] y = concat(labels(Labeler.BUY, 30), labels(Labeler.HOLD, 30));
        ModelStrategy strategy = ModelStrategyFactory.create(type);
        strategy.train(x, y, AnalysisType.TECHNICAL_FUNDAMENTAL);

        double[] pred = strategy.predict(point(0.2));
        assertTrue(pred[0] == Labeler.BUY || pred[0] == Labeler.HOLD,
                type.name() + " yalnız gözlenen sınıflardan birini dönmeli: " + pred[0]);
    }

    /**
     * Tek sınıflı eğitimde sabit tahmin üretilmesi.
     * <p>
     * Senaryo: 30 örneğin tamamı TUT etiketiyle eğitilir (k=1 sınıf). Beklenen
     * davranış: her sorguda, sorgu eğitim kümesinden uzak olsa bile TUT döner ve
     * skor pozitif olur (tek sınıf %100 güvenle sabitlenir).
     *
     * @param type sınanan model stratejisi
     */
    @ParameterizedTest
    @EnumSource(ModelType.class)
    void tekSinifliEgitimSabitTahminDoner(ModelType type) {
        double[][] x = blob(0.5, 30);
        int[] y = labels(Labeler.HOLD, 30);
        ModelStrategy strategy = ModelStrategyFactory.create(type);
        strategy.train(x, y, AnalysisType.TECHNICAL_FUNDAMENTAL);

        double[] pred = strategy.predict(point(0.9));
        assertEquals(Labeler.HOLD, (int) pred[0], type.name());
        assertTrue(pred[1] > 0, type.name() + " skoru pozitif olmalı");
    }

    /**
     * Eğitilmemiş KNN'in karar üretmeyen varsayılan sonucu döndürmesi.
     * <p>
     * Senaryo: {@link KnnStrategy} eğitilmeden doğrudan sorgulanır. Beklenen
     * davranış: varsayılan sınıf olarak {@link Labeler#HOLD} ile skor 0,0 döner
     * ({@code {HOLD, 0.0}}); sıfır güven, eğitimsiz modelin öneri üretmediğini
     * gösterir.
     */
    @Test
    void egitilmisModelVarsayilanSinifiTutmaz() {
        ModelStrategy strategy = new KnnStrategy();
        double[] pred = strategy.predict(point(0.5));
        assertEquals(Labeler.HOLD, (int) pred[0]);
        assertEquals(0.0, pred[1], 1e-9);
    }

    /**
     * KNN'in k=5 komşu çoğunluğuyla karar vermesi.
     * <p>
     * Senaryo: 0,50 çevresinde toplanmış 20 SAT örneği ve 0,44'te tek bir AL örneği
     * eğitilir; 0,45 sorgulanır — en yakın komşu tek AL örneğidir. Beklenen davranış:
     * en yakın 5 komşunun çoğunluğu SAT olduğu için tahmin SAT olur (k=1 olsaydı tek
     * yakın örnek BUY kazanırdı).
     */
    @Test
    void knnBesKomsuIleKararVerir() {
        List<double[]> xs = new ArrayList<>();
        List<Integer> ys = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            xs.add(point(0.50 + (i % 5 - 2) * 1e-3));
            ys.add(Labeler.SELL);
        }
        xs.add(point(0.44));
        ys.add(Labeler.BUY);
        double[][] x = xs.toArray(new double[0][]);
        int[] y = ys.stream().mapToInt(Integer::intValue).toArray();

        KnnStrategy knn = new KnnStrategy();
        knn.train(x, y, AnalysisType.TECHNICAL_FUNDAMENTAL);

        double[] pred = knn.predict(point(0.45));
        assertEquals(Labeler.SELL, (int) pred[0],
                "k=5 ile cogunluk sinif secilmeli; k=1 olsaydi BUY (0) secilirdi");
    }

    /**
     * {@link ClassSpace} etiketlerinin sıkıştırılıp geri açılması.
     * <p>
     * Senaryo: {@code {TUT, AL, AL}} etiketleriyle bir tablo kurulur. Beklenen
     * davranış: gözlenen 2 sınıf için {@code size()} 2 olur; artan sırada indeks 0
     * AL'ye, indeks 1 TUT'a geri açılır ve {@code {AL, TUT, AL}} sıkıştırıldığında
     * {@code {0, 1, 0}} ortaya çıkar.
     */
    @Test
    void classSpaceEtiketleriGeriDonusturur() {
        ClassSpace space = ClassSpace.of(new int[]{Labeler.HOLD, Labeler.BUY, Labeler.BUY});
        assertEquals(2, space.size());
        assertEquals(Labeler.BUY, space.label(0));
        assertEquals(Labeler.HOLD, space.label(1));
        assertArrayEquals(new int[]{0, 1, 0}, space.compress(new int[]{Labeler.BUY, Labeler.HOLD, Labeler.BUY}));
    }

    /**
     * Analiz tipinin belirlediği boyut kesilmiş matrislerle eğitim ve tahmin.
     * <p>
     * Senaryo: her analiz tipi ({@code YALNIZCA_TEKNIK} → 6, {@code YALNIZCA_TEMEL} → 5,
     * {@code TEKNIK_TEMEL} → 11 sütun) için o boyutta üretilmiş üç kompakt bulut
     * (0,2 / 0,5 / 0,8) AL / SAT / TUT etiketiyle eğitilir. Beklenen davranış: her
     * strateji ve her analiz tipi kombinasyonunda eğitim ve tahmin istisna fırlatmaz;
     * sorgu noktası doğru sınıfı döndürür (0,2 → AL, 0,5 → SAT, 0,8 → TUT).
     *
     * @param type sınanan model stratejisi
     */
    @ParameterizedTest
    @EnumSource(ModelType.class)
    void analizTipineGoreKesilmisBoyutlarlaCalisir(ModelType type) {
        for (AnalysisType a : AnalysisType.values()) {
            int dim = FeatureVector.dimension(a);
            double[][] x = concat(concat(blob(0.2, 30, dim), blob(0.5, 30, dim)), blob(0.8, 30, dim));
            int[] y = concat(concat(labels(Labeler.BUY, 30), labels(Labeler.SELL, 30)), labels(Labeler.HOLD, 30));
            ModelStrategy strategy = ModelStrategyFactory.create(type);
            strategy.train(x, y, a);

            assertEquals(Labeler.BUY, (int) strategy.predict(point(0.2, dim))[0], type + "/" + a);
            assertEquals(Labeler.SELL, (int) strategy.predict(point(0.5, dim))[0], type + "/" + a);
            assertEquals(Labeler.HOLD, (int) strategy.predict(point(0.8, dim))[0], type + "/" + a);
        }
    }
}