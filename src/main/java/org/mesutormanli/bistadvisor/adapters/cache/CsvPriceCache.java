package org.mesutormanli.bistadvisor.adapters.cache;

import org.mesutormanli.bistadvisor.config.AppConfig;
import org.mesutormanli.bistadvisor.domain.market.Bar;
import org.mesutormanli.bistadvisor.domain.market.MarketTime;
import org.mesutormanli.bistadvisor.ports.PriceCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.List;

/**
 * Hisse fiyat verilerini yapılandırılabilir dizinde (varsayılan {@code cache}) CSV dosyaları
 * olarak önbellekleyen {@link PriceCache} adaptörü.
 * <p>
 * Her sembol için {@code price_SEMBOL.csv} dosyası tutulur; satır biçimi codec ile
 * belirlenir (bkz. {@link BarCsvCodec}). Tazelik kararı {@link MarketTime} üzerinden,
 * 4 günlük tazelik penceresi kullanılarak verilir. Dosyadaki son satırın tarih alanı
 * çözümlenemiyorsa veri taze sayılmaz. G/Ç hataları istisna yaymaz.
 */
@Component
public class CsvPriceCache implements PriceCache {
    private static final Logger log = LoggerFactory.getLogger(CsvPriceCache.class);
    private final Path cacheDir;

    /**
     * Kurar ve önbellek dizinini hazırlar (Spring bileşeni olarak).
     *
     * @param appConfig önbellek dizinini belirleyen uygulama yapılandırması
     * @implNote Yapılandırılan dizin (varsayılan {@code cache}) mevcut değilse oluşturulur.
     */
    @Autowired
    public CsvPriceCache(AppConfig appConfig) {
        this(Path.of(appConfig.cacheDir()));
    }

    /**
     * Kurar ve verilen önbellek dizinini hazırlar (test/doğrudan kullanım için).
     *
     * @param cacheDir önbellek dizini
     * @implNote Dizin mevcut değilse oluşturulur.
     */
    public CsvPriceCache(Path cacheDir) {
        this.cacheDir = cacheDir;
        cacheDir.toFile().mkdirs();
    }

    /**
     * Sembolün önbellekte taze fiyat verisi tutup tutmadığını belirler.
     * <p>
     * Son satır biçimi {@code tarih,kapanış,hacim} kabul edilir; dosya yoksa, boşsa veya
     * son satırın tarih alanı çözümlenemiyorsa sonuç {@code false}'tur. Aksi hâlde tarih
     * {@link MarketTime#isFreshEnough(LocalDate)} ile 4 günlük tazelik penceresine göre sınanır.
     *
     * @param symbol hisse sembolü
     * @return önbellekteki son bar {@code bugün − 4} gününden eski değilse {@code true}
     */
    @Override
    public boolean hasFresh(String symbol) {
        Path f = priceFile(symbol);
        if (!f.toFile().exists()) return false;
        try {
            List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
            if (lines.isEmpty()) return false;
            String last = lines.getLast().trim();
            int comma = last.indexOf(',');
            String datePart = comma >= 0 ? last.substring(0, comma) : last;
            return MarketTime.isFreshEnough(LocalDate.parse(datePart));
        } catch (IOException | java.time.format.DateTimeParseException e) {
            return false;
        }
    }

    /**
     * Sembolün önbellek dosyasındaki barları okur.
     *
     * @param symbol hisse sembolü
     * @return çözümlenmiş bar listesi
     * @implNote G/Ç hatasında istisna yayılmaz, boş liste döner; bozuk satırlar codec
     *           tarafından sessizce atlanır.
     */
    @Override
    public List<Bar> read(String symbol) {
        try {
            return BarCsvCodec.decode(Files.readAllLines(priceFile(symbol), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Barları sembolün önbellek dosyasına yazar; dosya varsa içeriği tamamen değiştirilir.
     *
     * @param symbol hisse sembolü
     * @param bars   yazılacak bar listesi
     * @implNote G/Ç hatasında istisna yutulur ve durum {@code WARN} seviyesinde günlüğe yazılır.
     */
    @Override
    public void write(String symbol, List<Bar> bars) {
        try {
            Files.write(priceFile(symbol), BarCsvCodec.encode(bars), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.warn("cache yazma hatasi {}: {}", symbol, e.getMessage());
        }
    }

    /**
     * Sembolün önbellek dosyası yolunu hesaplar.
     *
     * @param symbol hisse sembolü
     * @return önbellek dizini altında {@code price_SEMBOL.csv} yolu (sembol büyük harfe çevrilir)
     */
    private Path priceFile(String symbol) {
        return cacheDir.resolve("price_" + symbol.toUpperCase() + ".csv");
    }
}