package org.mesutormanli.bistadvisor.domain.settings;

import org.mesutormanli.bistadvisor.domain.advisor.RiskProfile;

import java.time.LocalDate;

/**
 * Kullanıcıya ait danışman ayarları: risk profili, model türü, analiz tipi,
 * seçili endeks ve son analiz tarihi.
 * <p>
 * Değiştirilemez kayıttır; güncellemeler {@code with...} yöntemleriyle yeni
 * örnek üretilerek yapılır. Metin alanları (durum dosyasından gelen ham
 * değerler) enum'lara {@link RiskProfile#fromLabel(String)},
 * {@link ModelType#fromKey(String)} ve {@link AnalysisType#fromKey(String)} ile
 * çözülür; geçersiz değerler varsayılana düşer.
 *
 * @param riskProfile   risk profili
 * @param modelType     model türü
 * @param analysisType  analiz tipi (metrik kümesi)
 * @param selectedIndex seçili endeks adı
 * @param lastRunDate   son analiz tarihi ({@code null} olabilir)
 */
public record AdvisorSettings(RiskProfile riskProfile, ModelType modelType,
                              AnalysisType analysisType, String selectedIndex,
                              LocalDate lastRunDate) {

    /** Durum dosyası varsayılan seçili endeksi. */
    public static final String DEFAULT_INDEX = "BIST_30";

    /**
     * Varsayılan ayarlarla yeni bir örnek döndürür.
     *
     * @return dengeli mod, rastgele orman, teknik+temel analiz ve {@code BIST_30}
     */
    public static AdvisorSettings defaults() {
        return new AdvisorSettings(RiskProfile.BALANCED, ModelType.RANDOM_FOREST,
                AnalysisType.TECHNICAL_FUNDAMENTAL, DEFAULT_INDEX, null);
    }

    /**
     * Seçim alanlarını günceller; {@code null} verilen alan korunur.
     *
     * @param risk     risk profili adı/etiketi ({@code null} = korunur)
     * @param model    model anahtarı ({@code null} = korunur)
     * @param analysis analiz tipi anahtarı ({@code null} = korunur)
     * @param index    endeks adı ({@code null} = korunur)
     * @return güncellenmiş ayarlar
     */
    public AdvisorSettings withSelection(String risk, String model, String analysis, String index) {
        return new AdvisorSettings(
                risk != null ? RiskProfile.fromLabel(risk) : riskProfile,
                model != null ? ModelType.fromKey(model) : modelType,
                analysis != null ? AnalysisType.fromKey(analysis) : analysisType,
                index != null ? index : selectedIndex,
                lastRunDate);
    }

    /**
     * Son analiz tarihini günceller.
     *
     * @param date yeni son analiz tarihi
     * @return tarihi güncellenmiş ayarlar
     */
    public AdvisorSettings withLastRunDate(LocalDate date) {
        return new AdvisorSettings(riskProfile, modelType, analysisType, selectedIndex, date);
    }
}