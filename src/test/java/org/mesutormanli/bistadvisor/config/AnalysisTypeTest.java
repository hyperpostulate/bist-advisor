package org.mesutormanli.bistadvisor.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AnalysisType} çözümleme ve metrik kümesi yardımcılarının testleri:
 * anahtar/takma ad eşlemelerinin doğruluğu, bilinmeyen girdilerde varsayılana
 * düşülmesi ve {@code usesTechnical}/{@code usesFundamental} bayraklarının
 * metrik kümeleriyle tutarlılığı.
 */
class AnalysisTypeTest {

    /**
     * Anahtar, enum adı, görünen ad ve takma adların doğru çözümlenmesi.
     * <p>
     * Senaryo: her sözdizimi biçimi denenir — anahtar ({@code technical}), enum adı
     * ({@code TECHNICAL}), görünen ad ({@code TEKNIK}) ve kullanıcı takma adları
     * ({@code YALNIZCA_TEKNIK}, {@code YALNIZCA_TEMEL}, {@code TEKNIK_VE_TEMEL}).
     * Beklenen davranış: tümü büyük/küçük harfsiz eşleşir ve beklenen sabite karşılık
     * gelir.
     */
    @Test
    void fromKeyTumSozDizimleriniCozer() {
        assertEquals(AnalysisType.TECHNICAL, AnalysisType.fromKey("technical"));
        assertEquals(AnalysisType.TECHNICAL, AnalysisType.fromKey("TECHNICAL"));
        assertEquals(AnalysisType.TECHNICAL, AnalysisType.fromKey("teknik"));
        assertEquals(AnalysisType.TECHNICAL, AnalysisType.fromKey("YALNIZCA_TEKNIK"));
        assertEquals(AnalysisType.FUNDAMENTAL, AnalysisType.fromKey("temel"));
        assertEquals(AnalysisType.FUNDAMENTAL, AnalysisType.fromKey("yalnizca_temel"));
        assertEquals(AnalysisType.TECHNICAL_FUNDAMENTAL, AnalysisType.fromKey("technical_fundamental"));
        assertEquals(AnalysisType.TECHNICAL_FUNDAMENTAL, AnalysisType.fromKey("TEKNIK_TEMEL"));
        assertEquals(AnalysisType.TECHNICAL_FUNDAMENTAL, AnalysisType.fromKey("teknik_ve_temel"));
    }

    /**
     * Null ve tanınmayan girdilerde varsayılan analiz tipine düşülmesi.
     * <p>
     * Senaryo: {@code null} ve bilinmeyen bir metin çözümlenmeye çalışılır. Beklenen
     * davranış: her ikisi de {@link AnalysisType#TECHNICAL_FUNDAMENTAL} döndürür —
     * eksik/geçersiz alanlar mevcut (tüm metrikli) davranışı bozmaz.
     */
    @Test
    void fromKeyBilinmeyenGirdideVarsayilanaDuser() {
        assertEquals(AnalysisType.TECHNICAL_FUNDAMENTAL, AnalysisType.fromKey(null));
        assertEquals(AnalysisType.TECHNICAL_FUNDAMENTAL, AnalysisType.fromKey(""));
        assertEquals(AnalysisType.TECHNICAL_FUNDAMENTAL, AnalysisType.fromKey("olmayan_tip"));
    }

    /**
     * Metrik kümesi bayraklarının analiz tipleriyle tutarlılığı.
     * <p>
     * Senaryo: üç analiz tipi için {@code usesTechnical}/{@code usesFundamental}
     * bayrakları okunur. Beklenen davranış: yalnız teknik modda temel bayrak kapalı,
     * yalnız temel modda teknik bayrak kapalı, birleşik modda ikisi birden açıktır.
     */
    @Test
    void metrikKumesiBayraklariTutarlidir() {
        assertTrue(AnalysisType.TECHNICAL.usesTechnical());
        assertFalse(AnalysisType.TECHNICAL.usesFundamental());
        assertFalse(AnalysisType.FUNDAMENTAL.usesTechnical());
        assertTrue(AnalysisType.FUNDAMENTAL.usesFundamental());
        assertTrue(AnalysisType.TECHNICAL_FUNDAMENTAL.usesTechnical());
        assertTrue(AnalysisType.TECHNICAL_FUNDAMENTAL.usesFundamental());
    }
}