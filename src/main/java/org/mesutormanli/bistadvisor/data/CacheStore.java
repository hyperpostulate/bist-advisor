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
 * Hisse senedi fiyat verilerini CSV dosyaları halinde diskte önbelleğe alır.
 * <p>
 * Her sembol için {@code price_SEMBOL.csv} formatında bir dosya tutulur.
 * Önbellek dizini {@code AppConfig.cacheDir()} ile belirlenir. Tazelik kontrolü
 * {@link MarketTime} üzerinden, hafta sonu/tatil toleranslı bir pencereyle yapılır.
 */
@Component
public class CacheStore {
    private static final Logger log = LoggerFactory.getLogger(CacheStore.class);
    private final Path cacheDir;

    /**
     * Önbellek dizinini oluşturur (yoksa).
     *
     * @param appConfig uygulama yapılandırması
     */
    public CacheStore(AppConfig appConfig) {
        this.cacheDir = Path.of(appConfig.cacheDir());
        cacheDir.toFile().mkdirs();
    }

    /**
     * Belirtilen hisse için önbellekte taze veri olup olmadığını kontrol eder.
     * <p>
     * Tazelik kuralı: son bar'ın tarihi
     * {@link MarketTime#isFreshEnough(LocalDate)} ile "bugün − 4 gün" penceresi içindeyse
     * veri tazedir. Böylece hafta sonu/resmi tatilde (o gün barı oluşmayacakken) tüm
     * seriler gereksiz yere yeniden indirilmez.
     *
     * @param symbol hisse sembolü
     * @return {@code true} eğer önbellek tazeyse
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
     * Belirtilen hisse için önbellekteki tüm satırları okur.
     *
     * @param symbol hisse sembolü
     * @return satır listesi, dosya yoksa boş liste
     */
    public List<String> readLines(String symbol) {
        try {
            return Files.readAllLines(priceFile(symbol), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Belirtilen hisse için önbellek dosyasına satırları yazar (varsa üzerine yazar).
     *
     * @param symbol hisse sembolü
     * @param lines  yazılacak satırlar ({@code tarih,kapanis,hacim} formatında)
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
     * Bir hisse sembolü için önbellek dosyasının tam yolunu döndürür.
     *
     * @param symbol hisse sembolü
     * @return {@code {cacheDir}/price_SEMBOL.csv} yolu
     */
    private Path priceFile(String symbol) {
        return cacheDir.resolve("price_" + symbol.toUpperCase() + ".csv");
    }
}
