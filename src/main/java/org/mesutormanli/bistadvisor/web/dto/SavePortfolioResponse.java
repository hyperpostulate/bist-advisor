package org.mesutormanli.bistadvisor.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code POST /api/portfolio} yanıtının tip güvenli gövdesi: kaydetme sonucu.
 * <p>
 * {@code status} değerleri: {@code ok} (sorunsuz), {@code warning} (kaydedildi
 * ama kurallar ihlal edilmiş, bkz. {@code message}) ve {@code error} (taslak
 * geçersiz, kaydedilmedi). {@code message} yalnızca gerektiğinde serileştirilir.
 *
 * @param status  sonuç durumu
 * @param message detay/uyarı metni ({@code ok} durumunda {@code null})
 */
@JsonInclude(JsonInclude.NON_NULL)
public record SavePortfolioResponse(String status, String message) {}