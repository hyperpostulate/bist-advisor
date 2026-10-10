package org.mesutormanli.bistadvisor.domain.portfolio;

/**
 * Portföy kurallarının eşik ve sabitleri.
 * <p>
 * En fazla {@value #MAX_POSITIONS} pozisyon taşınabilir; yüzen nokta
 * karşılaştırmalarında {@value #TOLERANCE} TL tolerans kullanılır.
 */
public final class PortfolioPolicy {

    /** Taşınabilecek en fazla pozisyon sayısı. */
    public static final int MAX_POSITIONS = 5;

    /** Yüzen nokta karşılaştırmalarında kullanılan TL toleransı. */
    public static final double TOLERANCE = 0.01;

    private PortfolioPolicy() {}
}