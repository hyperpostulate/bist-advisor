package org.mesutormanli.bistadvisor.domain.portfolio;

import java.util.ArrayList;
import java.util.List;

/**
 * Durum dosyası ({@code state.yaml}) ile serileştirilen portföy anlık görüntüsü.
 * <p>
 * Düz bir Jackson POJO'sudur (public alanlarla tasarlanmıştır) ve dosya
 * biçimiyle birebir aynı anahtarları taşır: {@code budget}, {@code cash},
 * {@code advisorMode}, {@code modelType}, {@code analysisType},
 * {@code selectedIndex}, {@code positions}, {@code lastRunDate}.
 * Ayar alanları metin olarak saklanır; enum çözümlemesi okuyucu tarafta
 * (bkz. {@code application.PortfolioService}) yapılır, böylece eski/bozuk
 * dosyalar varsayılan değerlere düşer.
 */
public class PortfolioSnapshot {

    /** Başlangıç sermayesi (TL). */
    public double budget = 0.0;

    /** Serbest nakit (TL). */
    public double cash = 0.0;

    /** Yatırım modu adı (varsayılan {@code BALANCED}). */
    public String advisorMode = "BALANCED";

    /** Model adı (varsayılan {@code RANDOM_FOREST}). */
    public String modelType = "RANDOM_FOREST";

    /** Analiz tipi adı (varsayılan {@code TECHNICAL_FUNDAMENTAL}). */
    public String analysisType = "TECHNICAL_FUNDAMENTAL";

    /** Seçili endeks adı (varsayılan {@code BIST_30}). */
    public String selectedIndex = "BIST_30";

    /** Pozisyon listesi (varsayılan boş). */
    public List<Position> positions = new ArrayList<>();

    /** Son analiz tarihi ({@code YYYY-AA-GG}; {@code null} olabilir). */
    public String lastRunDate;
}