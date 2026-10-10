package org.mesutormanli.bistadvisor.domain.ml;

/**
 * Modelin tek bir öznitelik vektörü için ürettiği tahmin: sınıf etiketi ve güven skoru.
 *
 * @param classLabel tahmin edilen sınıf etiketi ({@link Labeler#BUY} / {@link Labeler#SELL} /
 *                   {@link Labeler#HOLD}; eğitilmemiş modellerde {@link Labeler#HOLD})
 * @param score      güven/skor değeri, [0, 1] aralığında (eğitilmemiş modellerde 0.0)
 */
public record Prediction(int classLabel, double score) {

    /** Eğitilmemiş model için varsayılan tahmin ({@code TUT, 0.0}). */
    public static final Prediction NONE = new Prediction(Labeler.HOLD, 0.0);

    /**
     * Tahminin AL (alım) sınıfına ait olup olmadığını bildirir.
     *
     * @return sınıf {@link Labeler#BUY} ise {@code true}
     */
    public boolean isBuy() {
        return classLabel == Labeler.BUY;
    }

    /**
     * Tahminin SAT (satım) sınıfına ait olup olmadığını bildirir.
     *
     * @return sınıf {@link Labeler#SELL} ise {@code true}
     */
    public boolean isSell() {
        return classLabel == Labeler.SELL;
    }
}