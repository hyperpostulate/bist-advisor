package org.mesutormanli.bistadvisor.data;

import org.mesutormanli.bistadvisor.config.AppConfig;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CacheStore {
    private final String cacheDir;
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public CacheStore(AppConfig appConfig) {
        this.cacheDir = appConfig.cacheDir();
        new File(cacheDir).mkdirs();
    }

    private Object lockFor(String symbol) {
        return locks.computeIfAbsent(symbol.toUpperCase(), k -> new Object());
    }

    public Path priceFile(String symbol) {
        return Path.of(cacheDir, "price_" + symbol.toUpperCase() + ".csv");
    }

    public boolean hasFresh(String symbol, LocalDate today) {
        File f = priceFile(symbol).toFile();
        if (!f.exists()) return false;
        synchronized (lockFor(symbol)) {
            try {
                List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
                if (lines.isEmpty()) return false;
                return lines.getLast().startsWith(today.toString());
            } catch (IOException e) {
                return false;
            }
        }
    }

    public List<String> readPrice(String symbol) {
        synchronized (lockFor(symbol)) {
            try {
                return Files.readAllLines(priceFile(symbol), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return new ArrayList<>();
            }
        }
    }

    public void writePrice(String symbol, List<String> lines) {
        synchronized (lockFor(symbol)) {
            try {
                Files.write(priceFile(symbol), lines, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                // cache yazimi kritik degil, sessizce gec
            }
        }
    }
}
