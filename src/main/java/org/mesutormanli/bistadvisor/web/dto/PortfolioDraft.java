package org.mesutormanli.bistadvisor.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.mesutormanli.bistadvisor.domain.portfolio.PositionDraft;

import java.util.List;

/**
 * {@code POST /api/portfolio} isteğinin tip güvenli gövdesi: kaydedilmek
 * istenen portföy taslağı.
 * <p>
 * Alan adları arayüz sözleşmesiyle (ve durum dosyasıyla) aynıdır; isteğe
 * eklenmiş bilinmeyen alanlar (örn. okumadan geri dönen {@code cash},
 * {@code lastRunDate}) yok sayılır. Pozisyonlar doğrulanmamış taslak olarak
 * taşınır; biçim kontrolleri {@code PortfolioValidator} ile yürütülür.
 *
 * @param budget        sermaye (TL)
 * @param advisorMode   yatırım modu adı/etiketi
 * @param modelType     model anahtarı/adı
 * @param analysisType  analiz tipi anahtarı/adı
 * @param selectedIndex endeks adı
 * @param positions     taslak pozisyonlar
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortfolioDraft(double budget, String advisorMode, String modelType,
                             String analysisType, String selectedIndex,
                             List<PositionDraft> positions) {}