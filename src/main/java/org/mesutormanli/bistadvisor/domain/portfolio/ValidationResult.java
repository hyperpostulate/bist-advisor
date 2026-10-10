package org.mesutormanli.bistadvisor.domain.portfolio;

import java.util.List;

/**
 * Doğrulama sonucu: hata mesajları listesi.
 * <p>
 * Hata yoksa {@link #ok()} {@code true} döner ve {@link #message()} {@code null}
 * döndürür; hata varsa mesajlar {@code " | "} ile birleşik tek metin olarak
 * sunulur.
 *
 * @param errors hata mesajları (hata yoksa boş)
 */
public record ValidationResult(List<String> errors) {

    /** Sorunsuz doğrulama sonucu. */
    public static final ValidationResult OK = new ValidationResult(List.of());

    /**
     * Doğrulamanın hatasız geçip geçmediğini bildirir.
     *
     * @return hata yoksa {@code true}
     */
    public boolean ok() {
        return errors.isEmpty();
    }

    /**
     * Hataları birleşik metin olarak döndürür.
     *
     * @return {@code " | "} ile birleşik hata metni; hata yoksa {@code null}
     */
    public String message() {
        return errors.isEmpty() ? null : String.join(" | ", errors);
    }
}