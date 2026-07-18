package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.config.ModelType;

/**
 * Model tipine gore strateji ornegi uretir.
 */
public final class ModelStrategyFactory {
    private ModelStrategyFactory() {
    }

    public static ModelStrategy create(ModelType type) {
        return switch (type) {
            case SVM -> new SvmStrategy();
            case KNN -> new KnnStrategy();
            default -> new RandomForestStrategy();
        };
    }
}
