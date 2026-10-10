package org.mesutormanli.bistadvisor.web.dto;

import java.util.List;

/**
 * {@code GET /api/portfolio-view} yanıtının tip güvenli gövdesi: portföyün
 * maliyet/güncel değer/kâr-zarar raporu.
 *
 * @param budget        sermaye (TL)
 * @param advisorMode   yatırım modu adı
 * @param modelType     model adı
 * @param analysisType  analiz tipi adı
 * @param positions     pozisyon satırları
 * @param availableCash serbest nakit (TL)
 * @param totalInvested toplam maliyet (TL)
 * @param totalCurrent  toplam güncel değer (TL)
 * @param equity        toplam varlık (nakit + güncel değer, TL)
 * @param missingPrices güncel fiyatı alınamayan semboller
 */
public record PortfolioViewDto(double budget, String advisorMode, String modelType,
                               String analysisType, List<PositionViewDto> positions,
                               double availableCash, double totalInvested, double totalCurrent,
                               double equity, List<String> missingPrices) {}