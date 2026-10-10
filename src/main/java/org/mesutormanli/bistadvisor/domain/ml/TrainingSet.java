package org.mesutormanli.bistadvisor.domain.ml;

/**
 * Bir eğitim setinin özellik matrisi ve etiket dizisini birlikte taşıyan kayıt.
 *
 * @param features özellik matrisi ({@code double[N][k]}; satırlar örnek, sütunlar öznitelik;
 *                 {@code k} seçilen analiz tipiyle {@code FeatureVector.dimension(type)} olur)
 * @param labels   sınıf etiketleri ({@code int[N]}; {@link Labeler#BUY}/{@link Labeler#SELL}/
 *                 {@link Labeler#HOLD})
 */
public record TrainingSet(double[][] features, int[] labels) {}