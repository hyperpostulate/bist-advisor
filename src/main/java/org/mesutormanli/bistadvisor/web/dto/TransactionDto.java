package org.mesutormanli.bistadvisor.web.dto;

/**
 * {@code POST /api/confirm} istek kaleminin tip güvenli gövdesi: uygulanacak
 * tek bir AL/SAT emri.
 *
 * @param symbol hisse sembolü
 * @param action işlem türü ({@code AL} ya da {@code SAT})
 * @param lots   işlem lot sayısı
 * @param price  işlem fiyatı (TL)
 */
public record TransactionDto(String symbol, String action, int lots, double price) {}