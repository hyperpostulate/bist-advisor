package org.mesutormanli.bistadvisor.web.dto;

import java.util.List;

/**
 * {@code POST /api/analyze} ve {@code GET /api/pending} yanıtlarının tip güvenli
 * gövdesi: günlük analiz turunun sonuçları.
 *
 * @param holdings      mevcut pozisyonlar için AL/TUT/SAT önerileri
 * @param buys          alım (AL) önerileri
 * @param availableCash portföyün kullanılabilir nakdi
 * @param positionCount mevcut pozisyon sayısı
 * @param maxPositions  izin verilen maksimum pozisyon sayısı
 * @param buySlots      doldurulabilecek boş alım slotu sayısı
 * @param warnings      kullanıcıya gösterilecek uyarı satırları
 */
public record AnalysisResponse(List<RecommendationDto> holdings, List<RecommendationDto> buys,
                               double availableCash, int positionCount, int maxPositions,
                               int buySlots, List<String> warnings) {}