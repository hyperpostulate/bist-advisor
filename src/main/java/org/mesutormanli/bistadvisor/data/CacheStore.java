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
 * Önbellek dizini {@code AppConfig.cacheDir()} ile belirlenir.
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
     * Belirtilen hisse için önbellekte taze (bugünün tarihini içeren) veri olup
     * olmadığını kontrol eder.
     *
     * @param symbol hisse sembolü
     * @param today  bugünün tarihi
     * @return {@code true} eğer önbellek tazeyse
     */
    public boolean hasFresh(String symbol, LocalDate today) {
        Path f = priceFile(symbol);
        if (!f.toFile().exists()) return false;
        try {
            List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
            return !lines.isEmpty() && lines.getLast().startsWith(today.toString());
        } catch (IOException e) {
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
