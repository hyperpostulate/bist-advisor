package org.mesutormanli.bistadvisor.portfolio;

import org.mesutormanli.bistadvisor.config.AdvisorMode;
import org.mesutormanli.bistadvisor.config.AnalysisType;
import org.mesutormanli.bistadvisor.config.ModelType;

import java.util.ArrayList;
import java.util.List;

/**
 * Durum dosyası (state.yaml) ile serileştirilen, değiştirilebilir portföy durumu DTO'su.
 * <p>
 * Spring'den bağımsız düz bir POJO'dur (Jackson için public alanlarla tasarlanmıştır).
 * Alanlar: {@code budget} başlangıç sermayesi (TL); {@code cash} serbest nakit (TL);
 * {@code advisorMode} danışman modu adı (varsayılan {@code BALANCED});
 * {@code modelType} model adı (varsayılan {@code RANDOM_FOREST});
 * {@code analysisType} son kullanılan analiz tipi (metrik kümesi; varsayılan
 * {@code TECHNICAL_FUNDAMENTAL});
 * {@code selectedIndex} endeks adı (varsayılan {@code BIST_30});
 * {@code positions} pozisyon listesi (varsayılan boş);
 * {@code lastRunDate} son analiz tarihi (null olabilir).
 */
public class PortfolioState {

    public double budget = 0.0;

    public double cash = 0.0;

    public String advisorMode = AdvisorMode.BALANCED.name();

    public String modelType = ModelType.RANDOM_FOREST.name();

    public String analysisType = AnalysisType.TECHNICAL_FUNDAMENTAL.name();

    public String selectedIndex = "BIST_30";

    public List<Position> positions = new ArrayList<>();

    public String lastRunDate;

}
