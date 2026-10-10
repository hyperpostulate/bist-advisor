package org.mesutormanli.bistadvisor.domain.portfolio;

/**
 * Henüz doğrulanmamış pozisyon taslağı: kullanıcı/arayüz girdisinin ham hâli.
 * <p>
 * {@link Position} aksine normalizasyon yapmaz; sembol/lots/maliyet kontrolleri
 * {@link PortfolioValidator} ile bu taslak üzerinden yürütülür.
 *
 * @param symbol  hisse sembolü (ham girdi)
 * @param lots    tutulmak istenen lot sayısı
 * @param avgCost lot başına ortalama maliyet (TL)
 */
public record PositionDraft(String symbol, int lots, double avgCost) {}