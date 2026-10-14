package org.mesutormanli.bistadvisor.application;

import org.mesutormanli.bistadvisor.domain.portfolio.Position;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Portföy rapor görünümünü üreten salt-okunur servis.
 * <p>
 * Tek sorumluluğu portföy muhasebesi ile güncel fiyatların
 * {@link PortfolioView} okuma modeline dönüştürülmesidir: pozisyon başına
 * maliyet/güncel değer/kâr-zarar hesaplanır ve toplamlar özetlenir. Fiyat
 * verisi olmayan pozisyon için sahte fiyat üretilmez; sembol
 * {@code missingPrices} listesine düşer ve değer alanları {@code null} kalır.
 */
@Service
public class PortfolioViewService {

    private final PortfolioService portfolioService;
    private final AnalysisService analysisService;

    /**
     * Kurar: portföy servisini ve güncel fiyat kaynağını enjekte eder.
     *
     * @param portfolioService portföy uygulama servisi
     * @param analysisService  güncel fiyatları sağlayan analiz servisi
     */
    public PortfolioViewService(PortfolioService portfolioService, AnalysisService analysisService) {
        this.portfolioService = portfolioService;
        this.analysisService = analysisService;
    }

    /**
     * Portföyün güncel rapor görünümünü hesaplar.
     *
     * @return pozisyon satırları ve toplamları içeren görünüm
     */
    public PortfolioView view() {
        var state = portfolioService.snapshot();
        Map<String, Double> prices = analysisService.currentPrices();
        double totalInvested = 0, totalCurrent = 0;
        List<PositionView> rows = new ArrayList<>();
        List<String> missingPrices = new ArrayList<>();
        for (Position p : state.positions) {
            Double cur = prices.get(p.symbol());
            if (cur == null) missingPrices.add(p.symbol());
            double costTotal = p.lots() * p.avgCost();
            totalInvested += costTotal;
            Double curTotal = null;
            Double pnlPct = null;
            Double pnlTl = null;
            if (cur != null) {
                curTotal = p.lots() * cur;
                totalCurrent += curTotal;
                pnlPct = p.avgCost() > 0 ? (cur - p.avgCost()) / p.avgCost() : 0.0;
                pnlTl = curTotal - costTotal;
            }
            rows.add(new PositionView(p.symbol(), p.lots(), p.avgCost(), costTotal,
                    cur, curTotal, pnlPct, pnlTl));
        }
        double cash = portfolioService.availableCash();
        return new PortfolioView(state.budget, state.advisorMode, state.modelType, state.analysisType,
                List.copyOf(rows), cash, totalInvested, totalCurrent, cash + totalCurrent,
                List.copyOf(missingPrices), portfolioService.maxPositions());
    }
}