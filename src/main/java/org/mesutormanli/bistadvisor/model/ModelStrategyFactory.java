package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.config.ModelType;

/**
 * {@link ModelType} sabitine göre uygun {@link ModelStrategy} örneğini
 * oluşturan factory sınıfı.
 */
public final class ModelStrategyFactory {
    private ModelStrategyFactory() {}

    /**
     * Belirtilen model türü için yeni bir strateji örneği oluşturur.
     *
     * @param type model türü ({@link ModelType#RANDOM_FOREST},
     *             {@link ModelType#SVM}, {@link ModelType#KNN})
     * @return yeni {@link ModelStrategy} örneği
     */
    public static ModelStrategy create(ModelType type) {
        return switch (type) {
            case SVM -> new SvmStrategy();
            case KNN -> new KnnStrategy();
            case RANDOM_FOREST -> new RandomForestStrategy();
        };
    }
}
