package org.mesutormanli.bistadvisor.portfolio;

import org.mesutormanli.bistadvisor.config.AdvisorMode;
import org.mesutormanli.bistadvisor.config.ModelType;

import java.util.ArrayList;
import java.util.List;

/**
 * Uygulamanin kalici durumu. CLI ve web ayni state.yaml dosyasini paylasir.
 */
public class PortfolioState {
    /** Yatirima ayrilan toplam TL butce. */
    public double budget = 0.0;
    /** Aktif öneri modu (TEMKINLI/DENGELI/AGRESIF). */
    public String advisorMode = AdvisorMode.BALANCED.name();
    /** Aktif ML modeli (RANDOM_FOREST/SVM/KNN). */
    public String modelType = ModelType.RANDOM_FOREST.name();
    /** Analiz yapilacak BIST endeksi (bist-indices.properties icindeki ad). */
    public String selectedIndex = "BIST_30";
    /** Mevcut pozisyonlar (maks 5 hisse). */
    public List<Position> positions = new ArrayList<>();
    /** Son calistirma tarihi (yyyy-MM-dd). */
    public String lastRunDate;

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isInitialized() {
        return positions != null && !positions.isEmpty() && budget > 0;
    }
}
