package org.mesutormanli.bistadvisor.data;

import org.mesutormanli.bistadvisor.config.AppConfig;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.List;

/** Fiyat serilerini cache/ dizininde CSV olarak saklar. Her sembol ayri bir dosyada tutulur. */
@Component
public class CacheStore {
    private final Path cacheDir;

    public CacheStore(AppConfig appConfig) {
        this.cacheDir = Path.of(appConfig.cacheDir());
        cacheDir.toFile().mkdirs();
    }

    /** Bugune ait veri var mi? (son satirin tarihi bugun ile basliyorsa taze kabul edilir.) */
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

    /** Sembolun cache dosyasindaki tum satirlari okur. */
    public List<String> readLines(String symbol) {
        try {
            return Files.readAllLines(priceFile(symbol), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Sembolun cache dosyasina satirlari yazar (varsa uzerine yazar). */
    public void writeLines(String symbol, List<String> lines) {
        try {
            Files.write(priceFile(symbol), lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            // cache yazimi kritik degil, sessizce gec
        }
    }

    /** Cache dosya yolunu dondurur: cache/price_SEMBOL.csv. */
    private Path priceFile(String symbol) {
        return cacheDir.resolve("price_" + symbol.toUpperCase() + ".csv");
    }
}
