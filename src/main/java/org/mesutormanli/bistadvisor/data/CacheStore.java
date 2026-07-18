package org.mesutormanli.bistadvisor.data;

import org.mesutormanli.bistadvisor.config.AppConfig;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Cekilen fiyat serilerini cache/ dizininde gunluk CSV olarak tutar.
 * Ag erisimi yoksa ofline fallback saglar.
 */
@Component
public class CacheStore {
    private final String cacheDir;

    public CacheStore(AppConfig appConfig) {
        this.cacheDir = appConfig.cacheDir();
        new File(cacheDir).mkdirs();
    }

    public Path priceFile(String symbol) {
        return Path.of(cacheDir, "price_" + symbol.toUpperCase() + ".csv");
    }

    public boolean hasFresh(String symbol, LocalDate today) {
        File f = priceFile(symbol).toFile();
        if (!f.exists()) return false;
        try {
            List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
            if (lines.isEmpty()) return false;
            String last = lines.get(lines.size() - 1);
            return last.startsWith(today.toString());
        } catch (IOException e) {
            return false;
        }
    }

    public List<String> readPrice(String symbol) {
        try {
            return Files.readAllLines(priceFile(symbol), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public void writePrice(String symbol, List<String> lines) {
        try (BufferedWriter w = Files.newBufferedWriter(priceFile(symbol), StandardCharsets.UTF_8)) {
            for (String l : lines) {
                w.write(l);
                w.write("\n");
            }
        } catch (IOException e) {
            // cache yazimi kritik degil, sessizce gec
        }
    }
}
