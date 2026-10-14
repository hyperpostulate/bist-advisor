package org.mesutormanli.bistadvisor.application;

import java.util.List;

/**
 * Portföyün rapor görünümü: maliyet, güncel değer ve kâr/zarar özetleri.
 *
 * @param budget        sermaye (TL)
 * @param advisorMode   yatırım modu adı
 * @param modelType     model adı
 * @param analysisType  analiz tipi adı
 * @param positions     pozisyon satırları
 * @param availableCash serbest nakit (TL)
 * @param totalInvested toplam maliyet (TL)
 * @param totalCurrent  toplam güncel değer (TL; fiyatı bilinmeyenler 0 sayılır)
 * @param equity        toplam varlık (nakit + güncel değer, TL)
 * @param missingPrices güncel fiyatı alınamayan semboller
 * @param maxPositions  izin verilen maksimum pozisyon sayısı
 */
public record PortfolioView(double budget, String advisorMode, String modelType, String analysisType,
                            List<PositionView> positions, double availableCash,
                            double totalInvested, double totalCurrent, double equity,
                            List<String> missingPrices, int maxPositions) {}