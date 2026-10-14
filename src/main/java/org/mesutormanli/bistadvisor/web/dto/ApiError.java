package org.mesutormanli.bistadvisor.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Yakalanan uç nokta hatalarının tip güvenli gövdesi.
 * <p>
 * Arayüz {@code post()} yardımcı fonksiyonu {@code status}/{@code message}
 * alanlarını bekler; {@code status} her zaman {@code error}'dur. {@code message}
 * yalnızca doluysa serileştirilir.
 *
 * @param status  sabit hata durumu ({@code error})
 * @param message kullanıcıya gösterilecek hata açıklaması
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String status, String message) {}
