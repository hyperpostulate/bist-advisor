package org.mesutormanli.bistadvisor.domain.market;

/**
 * Günlük fiyat verisini temsil eden değiştirilemez kayıt.
 *
 * @param date   bar tarihi ({@code YYYY-AA-GG})
 * @param close  kapanış fiyatı
 * @param volume işlem hacmi
 */
public record Bar(String date, double close, double volume) {}