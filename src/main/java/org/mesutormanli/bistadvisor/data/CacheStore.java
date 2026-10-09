package org.mesutormanli.bistadvisor.data;

import org.mesutormanli.bistadvisor.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.List;

/**
 * Hisse fiyat verilerini yapılandırılabilir dizinde (varsayılan {@code cache}) CSV dosyaları olarak önbellekler.
 * <p>
 * Her sembol için {@code price_SEMBOL.csv} dosyası tutulur; satır biçimi
 * {@code tarih,kapanış,hacim}'dir. Tazelik kararı {@link MarketTime} üzerinden,
 * 4 günlük tazelik penceresi kullanılarak verilir.
 */
@Component
public class CacheStore {
    private static final Logger log = LoggerFactory.getLogger(CacheStore.class);
    private final Path cacheDir;

    /**
     * Kurar ve önbellek dizinini hazırlar.
     *
     * @param appConfig önbellek dizinini belirleyen uygulama yapılandırması
     * @implNote Yapılandırılan dizin (varsayılan {@code cache}) mevcut değilse oluşturulur.
     */
    public CacheStore(AppConfig appConfig) {
        this.cacheDir = Path.of(appConfig.cacheDir());
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
     * Sembolün önbellek dosyasındaki tüm satırları okur.
     *
     * @param symbol hisse sembolü
     * @return {@code tarih,kapanış,hacim} biçimindeki CSV satırları
     * @implNote G/Ç hatasında istisna yayılmaz, boş liste döner.
     */
    public List<String> readLines(String symbol) {
        try {
            return Files.readAllLines(priceFile(symbol), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Satırları sembolün önbellek dosyasına yazar; dosya varsa içeriği tamamen değiştirilir.
     *
     * @param symbol hisse sembolü
     * @param lines  yazılacak CSV satırları ({@code tarih,kapanış,hacim} biçiminde)
     * @implNote G/Ç hatasında istisna yutulur ve durum {@code WARN} seviyesinde günlüğe yazılır.
     */
    public void writeLines(String symbol, List<String> lines) {
        try {
            Files.write(priceFile(symbol), lines, StandardCharsets.UTF_8,
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
