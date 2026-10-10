package org.mesutormanli.bistadvisor.web.dto;

/**
 * Öneri listelerindeki tek AL/TUT/SAT kalemi.
 *
 * @param index  önerinin listedeki sıra numarası
 * @param symbol önerilen hissenin sembolü
 * @param action işlem sinyali ({@code AL}, {@code TUT} ya da {@code SAT})
 * @param lots   önerilen lot adedi
 * @param price  kararın dayanak fiyatı; {@code null} = fiyat verisi yok
 * @param score  model skoru/güveni; {@code null} = tahmin yok
 * @param note   kararın gerekçesini özetleyen not
 */
public record RecommendationDto(int index, String symbol, String action,
                                int lots, Double price, Double score, String note) {}