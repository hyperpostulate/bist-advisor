package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.config.ModelType;

/**
 * {@link ModelType} sabitini uygun {@link ModelStrategy} örneğine eşleyen fabrika
 * (factory) yardımcı sınıfı.
 *
 * <p>Tüm üyeleri statiktir; sınıf örneklenemez. Üretilen her strateji eğitilmemiş
 * başlar ve çağıran tarafından eğitilir.
 */
public final class ModelStrategyFactory {
    /**
     * Kurar: yardımcı sınıfın örneklenmesini engelleyen özel yapıcı.
     */
    private ModelStrategyFactory() {}

    /**
     * Oluşturur: model türüne karşılık gelen yeni strateji örneğini kurar.
     *
     * @param type model türü ({@link ModelType#RANDOM_FOREST}, {@link ModelType#KNN}
     *             veya {@link ModelType#SVM})
     * @return yeni, henüz eğitilmemiş {@link ModelStrategy} örneği
     */
    public static ModelStrategy create(ModelType type) {
        return switch (type) {
            case SVM -> new SvmStrategy();
            case KNN -> new KnnStrategy();
            case RANDOM_FOREST -> new RandomForestStrategy();
        };
    }
}
