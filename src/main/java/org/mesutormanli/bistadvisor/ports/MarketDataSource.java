package org.mesutormanli.bistadvisor.ports;

import org.mesutormanli.bistadvisor.domain.market.Bar;
import org.mesutormanli.bistadvisor.domain.market.Fundamentals;

import java.util.List;

/**
 * Piyasa verisi kaynağına erişimin dışa açık sözleşmesi (port).
 * <p>
 * Uygulama katmanı fiyat serisi ve temel gösterge erişimini bu arayüz üzerinden yapar;
 * somut kaynak (ör. Yahoo Finance) adaptör katmanında bu sözleşmeyi uygular. Ağ hatası
 * ya da veri yokluğu istisna yaymaz: fiyat için boş liste, temel gösterge için
 * {@link Fundamentals#EMPTY} döner.
 */
public interface MarketDataSource {

    /**
     * Sembolün son 1 yıllık günlük fiyat serisini getirir.
     *
     * @param symbol hisse/endeks sembolü
     * @return kapanış ve hacim içeren bar listesi; hata veya veri yoksa boş liste
     */
    List<Bar> fetchPrices(String symbol);

    /**
     * Sembolün temel göstergelerini (F/K, PD/DD, temettü verimi, kâr büyümesi, ROE) getirir.
     *
     * @param symbol hisse sembolü
     * @return temel göstergeler; hata durumunda {@link Fundamentals#EMPTY}
     */
    Fundamentals fetchFundamentals(String symbol);
}