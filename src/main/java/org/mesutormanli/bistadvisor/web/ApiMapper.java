package org.mesutormanli.bistadvisor.web;

import org.mesutormanli.bistadvisor.application.AnalysisResult;
import org.mesutormanli.bistadvisor.application.PortfolioView;
import org.mesutormanli.bistadvisor.application.PositionView;
import org.mesutormanli.bistadvisor.application.Recommendation;
import org.mesutormanli.bistadvisor.web.dto.AnalysisResponse;
import org.mesutormanli.bistadvisor.web.dto.PortfolioViewDto;
import org.mesutormanli.bistadvisor.web.dto.PositionViewDto;
import org.mesutormanli.bistadvisor.web.dto.RecommendationDto;

import java.util.List;

/**
 * Uygulama katmanı okuma modellerini HTTP API gövdelerine (web.dto.*) çeviren
 * çift yönlü dönüşüm noktası.
 * <p>
 * Tek sorumluluğu katman sözleşmeleri arasındaki eşlemedir; iş kuralı içermez.
 * Böylece API şeması uygulama içi modellerden bağımsız evrimleşebilir.
 */
public final class ApiMapper {

    private ApiMapper() {}

    /**
     * Tek öneriyi API kalemine çevirir.
     *
     * @param r uygulama katmanı önerisi
     * @return API öneri kalemi (sinyal etiket metnine çevrilir)
     */
    public static RecommendationDto toDto(Recommendation r) {
        return new RecommendationDto(r.index(), r.symbol(), r.action().label(),
                r.lots(), r.price(), r.score(), r.note());
    }

    /**
     * Öneri listesini API listesine çevirir.
     *
     * @param recommendations öneriler
     * @return API öneri kalemleri
     */
    public static List<RecommendationDto> toDto(List<Recommendation> recommendations) {
        return recommendations.stream().map(ApiMapper::toDto).toList();
    }

    /**
     * Analiz sonucu API gövdesine çevirir.
     *
     * @param r analiz sonucu
     * @return API analiz gövdesi
     */
    public static AnalysisResponse toDto(AnalysisResult r) {
        return new AnalysisResponse(toDto(r.holdings()), toDto(r.buys()),
                r.availableCash(), r.positionCount(), r.maxPositions(),
                r.buySlots(), r.warnings());
    }

    /**
     * Portföy raporunu API gövdesine çevirir.
     *
     * @param view portföy görünümü
     * @return API portföy görünümü gövdesi
     */
    public static PortfolioViewDto toDto(PortfolioView view) {
        List<PositionViewDto> rows = view.positions().stream()
                .map(ApiMapper::toDto)
                .toList();
        return new PortfolioViewDto(view.budget(), view.advisorMode(), view.modelType(),
                view.analysisType(), rows, view.availableCash(), view.totalInvested(),
                view.totalCurrent(), view.equity(), view.missingPrices());
    }

    /**
     * Tek pozisyon rapor satırını API satırına çevirir.
     *
     * @param p pozisyon görünümü
     * @return API pozisyon satırı
     */
    public static PositionViewDto toDto(PositionView p) {
        return new PositionViewDto(p.symbol(), p.lots(), p.avgCost(), p.costTotal(),
                p.currentPrice(), p.currentTotal(), p.pnlPct(), p.pnlTl());
    }
}