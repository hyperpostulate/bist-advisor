package org.mesutormanli.bistadvisor.domain.portfolio;

/**
 * Tek bir AL/SAT emri: sembol, işlem türü, lot adedi ve birim fiyat.
 *
 * @param symbol hisse sembolü
 * @param action işlem türü ({@link TransactionAction#AL} ya da {@link TransactionAction#SAT})
 * @param lots   işlem lot sayısı
 * @param price  işlem fiyatı (TL)
 */
public record Transaction(String symbol, TransactionAction action, int lots, double price) {}