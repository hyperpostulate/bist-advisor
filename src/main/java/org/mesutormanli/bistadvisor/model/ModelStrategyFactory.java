package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.config.ModelType;

/** ModelType enum'ina gore uygun ModelStrategy ornegi uretir. */
public final class ModelStrategyFactory {
    private ModelStrategyFactory() {}

    /** Verilen tip icin yeni bir strateji ornegi olusturur. */
    public static ModelStrategy create(ModelType type) {
        return switch (type) {
            case SVM -> new SvmStrategy();
            case KNN -> new KnnStrategy();
            default -> new RandomForestStrategy();
        };
    }
}
