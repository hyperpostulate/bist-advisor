package org.mesutormanli.bistadvisor.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mesutormanli.bistadvisor.config.ModelType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ModelStrategy} uygulamalarının ortak davranış testleri.
 * <p>
 * Üç model için de şunlar doğrulanır: ayırt edici sınıfları öğrenme, eğitim setinde
 * bir sınıf <em>eksikken</em> istisna fırlatmama (SMILE olasılık dizisi boyutu tuzağı)
 * ve tek sınıflı eğitimde sabit tahmin. Ek olarak KNN'in gerçekten k=5 komşu ile
 * karar verdiği doğrulanır (tek komşu (k=1) farklı bir sınıf seçerdi).
 * <p>
 * Testler sentetik, 11 boyutlu ve tamamen deterministiktir; ağa çıkmaz.
 */
class ModelStrategiesTest {

    private static final int DIMS = 11;

    /** Merkezi {@code center} olan {@code n} örneklik deterministik bir öznitelik kümesi üretir. */
    private static double[][] blob(double center, int n) {
        double[][] x = new double[n][DIMS];
        for (int i = 0; i < n; i++) {
            double jitter = (i % 5 - 2) * 1e-3;
            Arrays.fill(x[i], center + jitter);
        }
        return x;
    }

    private static double[] point(double value) {
        double[] x = new double[DIMS];
        Arrays.fill(x, value);
        return x;
    }

    private static double[][] concat(double[][] a, double[][] b) {
        double[][] out = new double[a.length + b.length][];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static int[] labels(int value, int n) {
        int[] y = new int[n];
        Arrays.fill(y, value);
        return y;
    }

    private static int[] concat(int[] a, int[] b) {
        int[] out = new int[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    @ParameterizedTest
    @EnumSource(ModelType.class)
    void ayirtEdiciSiniflariOgrenir(ModelType type) {
        double[][] x = concat(concat(blob(0.2, 30), blob(0.5, 30)), blob(0.8, 30));
        int[] y = concat(concat(labels(Labeler.BUY, 30), labels(Labeler.SELL, 30)), labels(Labeler.HOLD, 30));
        ModelStrategy strategy = ModelStrategyFactory.create(type);
        strategy.train(x, y);

        assertEquals(Labeler.BUY, (int) strategy.predict(point(0.2))[0], type.name());
        assertEquals(Labeler.SELL, (int) strategy.predict(point(0.5))[0], type.name());
        assertEquals(Labeler.HOLD, (int) strategy.predict(point(0.8))[0], type.name());
        assertTrue(strategy.predict(point(0.2))[1] > 0.5, type.name() + " skoru 0.5 üstü olmalı");
    }

    @ParameterizedTest
    @EnumSource(ModelType.class)
    void eksikSinifHatasiFirlatmaz(ModelType type) {
        // SAT (1) etiketi hiç oluşmuyor: sınıf-eksikliği durumunda tahmin istisna
        // fırlatmamalı (eski davranış: IllegalArgumentException "Invalid posteriori vector size").
        double[][] x = concat(blob(0.2, 30), blob(0.8, 30));
        int[] y = concat(labels(Labeler.BUY, 30), labels(Labeler.HOLD, 30));
        ModelStrategy strategy = ModelStrategyFactory.create(type);
        strategy.train(x, y);

        double[] pred = strategy.predict(point(0.2));
        assertTrue(pred[0] == Labeler.BUY || pred[0] == Labeler.HOLD,
                type.name() + " yalnız gözlenen sınıflardan birini dönmeli: " + pred[0]);
    }

    @ParameterizedTest
    @EnumSource(ModelType.class)
    void tekSinifliEgitimSabitTahminDoner(ModelType type) {
        double[][] x = blob(0.5, 30);
        int[] y = labels(Labeler.HOLD, 30);
        ModelStrategy strategy = ModelStrategyFactory.create(type);
        strategy.train(x, y);

        double[] pred = strategy.predict(point(0.9));
        assertEquals(Labeler.HOLD, (int) pred[0], type.name());
        assertTrue(pred[1] > 0, type.name() + " skoru pozitif olmalı");
    }

    @Test
    void egitilmisModelVarsayilanSinifiTutmaz() {
        ModelStrategy strategy = new KnnStrategy();
        double[] pred = strategy.predict(point(0.5));
        assertEquals(Labeler.HOLD, (int) pred[0]);
        assertEquals(0.0, pred[1], 1e-9);
    }

    @Test
    void knnBesKomsuIleKararVerir() {
        // 20 örnek sınıf 1 (0.50 çevresi), sorguya tek ama çok yakın 1 örnek sınıf 0 (0.44).
        // k=5 ile çoğunluk sınıf 1 olmalı; k=1 olsaydı en yakın komşu (sınıf 0) kazanırdı.
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
        knn.train(x, y);

        double[] pred = knn.predict(point(0.45));
        assertEquals(Labeler.SELL, (int) pred[0],
                "k=5 ile cogunluk sinif secilmeli; k=1 olsaydi BUY (0) secilirdi");
    }

    @Test
    void classSpaceEtiketleriGeriDonusturur() {
        ClassSpace space = ClassSpace.of(new int[]{Labeler.HOLD, Labeler.BUY, Labeler.BUY});
        assertEquals(2, space.size());
        assertEquals(Labeler.BUY, space.label(0));
        assertEquals(Labeler.HOLD, space.label(1));
        assertArrayEquals(new int[]{0, 1, 0}, space.compress(new int[]{Labeler.BUY, Labeler.HOLD, Labeler.BUY}));
    }
}