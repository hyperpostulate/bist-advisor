package org.mesutormanli.bistadvisor.portfolio;

import org.mesutormanli.bistadvisor.config.AdvisorMode;
import org.mesutormanli.bistadvisor.config.ModelType;

import java.util.ArrayList;
import java.util.List;

/** state.yaml ile kalicilastirilan uygulama durumu: butce, mod, model, pozisyonlar. */
public class PortfolioState {
    /** Yatirima ayrilan toplam TL butce. */
    public double budget = 0.0;
    /** Aktif risk modu (CONSERVATIVE / BALANCED / AGGRESSIVE). */
    public String advisorMode = AdvisorMode.BALANCED.name();
    /** Aktif ML modeli (RANDOM_FOREST / SVM / KNN). */
    public String modelType = ModelType.RANDOM_FOREST.name();
    /** Analiz yapilacak BIST endeksi (bist-indices.properties icindeki anahtar). */
    public String selectedIndex = "BIST_30";
    /** Mevcut pozisyonlar (azami 5). */
    public List<Position> positions = new ArrayList<>();
    /** Son analiz tarihi (yyyy-MM-dd). */
    public String lastRunDate;

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isInitialized() {
        return positions != null && !positions.isEmpty() && budget > 0;
    }
}
