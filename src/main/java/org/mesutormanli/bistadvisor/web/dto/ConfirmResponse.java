package org.mesutormanli.bistadvisor.web.dto;

/**
 * {@code POST /api/confirm} yanıtının tip güvenli gövdesi: işlem uygulama özeti.
 *
 * @param status  sonuç durumu (her zaman {@code ok}; red sayıları sayaçlardadır)
 * @param applied uygulanan işlem sayısı
 * @param failed  reddedilen işlem sayısı
 */
public record ConfirmResponse(String status, int applied, int failed) {}