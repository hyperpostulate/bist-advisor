package org.mesutormanli.bistadvisor.application;

import java.util.List;

/**
 * Bir analiz turunun tüm sonuçlarını kapsüler.
 *
 * @param holdings      mevcut pozisyonlar için AL/TUT/SAT önerileri
 * @param buys          alım (AL) önerileri
 * @param availableCash portföyün kullanılabilir nakdi
 * @param positionCount mevcut pozisyon sayısı
 * @param maxPositions  izin verilen maksimum pozisyon sayısı
 * @param buySlots      doldurulabilecek boş alım slotu sayısı
 * @param warnings      kullanıcıya gösterilecek uyarı satırları
 */
public record AnalysisResult(List<Recommendation> holdings, List<Recommendation> buys,
                             double availableCash, int positionCount,
                             int maxPositions, int buySlots, List<String> warnings) {}