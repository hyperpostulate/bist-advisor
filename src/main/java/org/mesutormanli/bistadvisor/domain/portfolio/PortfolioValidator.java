package org.mesutormanli.bistadvisor.domain.portfolio;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Portföy doğrulama kurallarının tek toplandığı sınıf.
 * <p>
 * İki denetim katmanı vardır: {@link #structural(double, List)} kullanıcı/arayüz
 * girdisinin (portföy taslağı) biçim kontrollerini yapar; {@link #invariants(Portfolio)}
 * ise çalışan portföyün kurallarını (negatif nakit, pozisyon slot sınırı) tarar.
 * Sembol deseni {@code [A-Z0-9]{1,15}} ve tolerans {@link PortfolioPolicy}
 * değerleridir.
 */
public final class PortfolioValidator {

    private static final Pattern SYMBOL_PATTERN = Pattern.compile("[A-Z0-9]{1,15}");

    private PortfolioValidator() {}

    /**
     * Gelen portföy taslağının yapısal doğrulama hatalarını toplar.
     * <p>
     * Kurallar: bütçe sonlu ve ≥ 0 olmalı; pozisyonda sembol boş olamaz ve
     * {@code [A-Z0-9]{1,15}} desenine uymalı; lot &gt; 0 olmalı; ortalama maliyet
     * sonlu ve &gt; 0 olmalı.
     *
     * @param budget taslak bütçe
     * @param drafts taslak pozisyonlar ({@code null} olabilir)
     * @return hata mesajları içeren sonuç (hata yoksa {@link ValidationResult#OK})
     */
    public static ValidationResult structural(double budget, List<PositionDraft> drafts) {
        List<String> errors = new ArrayList<>();
        if (!Double.isFinite(budget) || budget < 0) {
            errors.add("Gecersiz butce: " + budget);
        }
        if (drafts != null) {
            for (PositionDraft p : drafts) {
                if (p == null || p.symbol() == null || p.symbol().isBlank()) {
                    errors.add("Pozisyonda sembol eksik");
                    continue;
                }
                if (!SYMBOL_PATTERN.matcher(p.symbol().trim().toUpperCase()).matches()) {
                    errors.add("Gecersiz sembol: " + p.symbol());
                }
                if (p.lots() <= 0) {
                    errors.add(p.symbol() + ": lot sayisi pozitif olmali (" + p.lots() + ")");
                }
                if (!(p.avgCost() > 0) || !Double.isFinite(p.avgCost())) {
                    errors.add(p.symbol() + ": birim maliyet pozitif olmali (" + p.avgCost() + ")");
                }
            }
        }
        return new ValidationResult(errors);
    }

    /**
     * Portföy kurallarını tarar ve ihlalleri bildirir.
     * <p>
     * Denetlenen kurallar: negatif nakit (−{@link PortfolioPolicy#TOLERANCE}
     * altındaki değerler) ve {@link PortfolioPolicy#MAX_POSITIONS} pozisyon
     * sınırının aşılması.
     *
     * @param portfolio taranacak portföy
     * @return sorun yoksa {@link ValidationResult#OK}; varsa hatalı sonuç
     */
    public static ValidationResult invariants(Portfolio portfolio) {
        List<String> errors = new ArrayList<>();
        double cash = portfolio.availableCash();
        if (cash < -PortfolioPolicy.TOLERANCE) {
            errors.add("Negatif nakit (" + String.format("%.0f", cash) + " TL)");
        }
        int count = portfolio.positionCount();
        if (count > PortfolioPolicy.MAX_POSITIONS) {
            errors.add("Pozisyon sayisi (" + count + ") siniri ("
                    + PortfolioPolicy.MAX_POSITIONS + ") asiyor");
        }
        return new ValidationResult(errors);
    }
}