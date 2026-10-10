package org.mesutormanli.bistadvisor.web.dto;

import java.util.List;

/**
 * {@code GET /api/config} yanıtının tip güvenli gövdesi: arayüzlerin seçim
 * listeleri ve o anki seçimler.
 *
 * @param modes               yatırım modu adları
 * @param models              model adları
 * @param analysisTypes       analiz tipi adları
 * @param indices             tanımlı endeks adları
 * @param currentMode         seçili yatırım modu
 * @param currentModel        seçili model
 * @param currentAnalysisType seçili analiz tipi
 * @param currentIndex        seçili endeks
 */
public record ConfigDto(List<String> modes, List<String> models, List<String> analysisTypes,
                        List<String> indices, String currentMode, String currentModel,
                        String currentAnalysisType, String currentIndex) {}