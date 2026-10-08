package org.mesutormanli.bistadvisor.portfolio;

import org.mesutormanli.bistadvisor.config.AdvisorMode;
import org.mesutormanli.bistadvisor.config.ModelType;

import java.util.ArrayList;
import java.util.List;

/**
 * Portföyün kalıcı durumunu temsil eden POJO.
 * <p>
 * Alanlar doğrudan Jackson/YAML serileştirmesi için {@code public} olarak
 * tanımlanmıştır. Varsayılan değerler: bütçe=0, mod=BALANCED,
 * model=RANDOM_FOREST, endeks=BIST_30.
 */
public class PortfolioState {

    /**
     * Toplam sermaye katkısı (TL): kullanıcının portföye koyduğu para. Alım/satım
     * işlemleri bu alanı değiştirmez; kullanıcı para yatırma/çekme yaptığında
     * güncellenir.
     */
    public double budget = 0.0;

    /**
     * Kullanılabilir nakit (TL). Alımda azalır (lot × alış fiyatı), satımda artar
     * (lot × satış fiyatı) — gerçekleşen kâr/zarar bu şekilde nakde yansır.
     */
    public double cash = 0.0;

    /** Yatırım modu (enum adı olarak saklanır) */
    public String advisorMode = AdvisorMode.BALANCED.name();

    /** ML model türü (enum adı olarak saklanır) */
    public String modelType = ModelType.RANDOM_FOREST.name();

    /** Seçili BIST endeksi (ör. BIST_30) */
    public String selectedIndex = "BIST_30";

    /** Portföydeki pozisyonlar listesi */
    public List<Position> positions = new ArrayList<>();

    /** Son analiz çalıştırma tarihi ({@code YYYY-MM-DD}) */
    public String lastRunDate;

}
