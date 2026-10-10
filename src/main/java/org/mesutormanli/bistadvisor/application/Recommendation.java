package org.mesutormanli.bistadvisor.application;

import org.mesutormanli.bistadvisor.domain.advisor.Signal;

/**
 * Numaralandırılmış tek bir AL/TUT/SAT önerisi.
 *
 * @param index  önerinin listedeki sıra numarası
 * @param symbol önerilen hissenin sembolü
 * @param action işlem sinyali ({@link Signal#AL}, {@link Signal#TUT} ya da {@link Signal#SAT})
 * @param lots   önerilen lot adedi
 * @param price  kararın dayanak fiyatı; {@code null} = fiyat verisi yok
 * @param score  model skoru/güveni; {@code null} = tahmin yok
 * @param note   kararın gerekçesini özetleyen not
 */
public record Recommendation(int index, String symbol, Signal action,
                             int lots, Double price, Double score, String note) {}