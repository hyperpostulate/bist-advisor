package org.mesutormanli.bistadvisor.domain.portfolio;

/**
 * Bir işlemin uygulanma sonucu ve reddedilme gerekçesi.
 * <p>
 * Yalnızca {@link #APPLIED} uygulanmış işlemi temsil eder; diğer sabitler
 * doğrulama/red nedenlerini taşır ve {@link #detail()} ile günlüğe yazılacak
 * açıklamayı üretir.
 */
public enum TransactionOutcome {
    APPLIED("islem uygulandi"),
    INVALID_ACTION("gecersiz islem"),
    BLANK_SYMBOL("sembol bos, islem atlandi"),
    INVALID_LOTS("lot sayisi pozitif olmali"),
    INVALID_PRICE("fiyat pozitif olmali"),
    INSUFFICIENT_CASH("yetersiz nakit"),
    POSITION_LIMIT("maks pozisyon siniri asildi"),
    NOT_HELD("portfoyde bulunamadi"),
    INSUFFICIENT_LOTS("yetersiz lot");

    private final String detail;

    TransactionOutcome(String detail) {
        this.detail = detail;
    }

    /**
     * Sonucun uygulanmış işlem olup olmadığını bildirir.
     *
     * @return işlem uygulandıysa {@code true}
     */
    public boolean applied() {
        return this == APPLIED;
    }

    /**
     * Sonucun günlüğe yazılacak kısa açıklamasını döndürür.
     *
     * @return açıklama metni
     */
    public String detail() {
        return detail;
    }
}