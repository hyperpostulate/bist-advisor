package org.mesutormanli.bistadvisor.data;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BIST endeks tanımlarını {@code bist-indices.properties} dosyasından yükler
 * ve endeks adına göre sembol listelerine erişim sağlar.
 * <p>
 * Her satır {@code ENDEKS_ADI=SEMBOL1,SEMBOL2,...} formatındadır.
 */
@Component
public class BistIndices {

    @Value("classpath:bist-indices.properties")
    private Resource indicesResource;

    private final Map<String, List<String>> indices = new LinkedHashMap<>();

    /**
     * {@code bist-indices.properties} dosyasını okur ve endeksleri belleğe yükler.
     * Boş satırlar ve {@code #} ile başlayan yorumlar atlanır.
     *
     * @throws IOException dosya okunamazsa fırlatılır
     */
    @PostConstruct
    void load() throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(indicesResource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String name = line.substring(0, eq).trim().toUpperCase();
                List<String> syms = new ArrayList<>();
                for (String s : line.substring(eq + 1).split(",")) {
                    String sym = s.trim().toUpperCase();
                    if (!sym.isEmpty()) syms.add(sym);
                }
                if (!syms.isEmpty()) indices.put(name, syms);
            }
        }
    }

    /**
     * Tüm endeks adlarının bir kopyasını döndürür.
     */
    public List<String> indexNames() { return List.copyOf(indices.keySet()); }

    /**
     * Belirtilen endekse ait hisse senedi sembollerini döndürür.
     *
     * @param indexName endeks adı (case-insensitive)
     * @return sembol listesi, endeks bulunamazsa boş liste
     */
    public List<String> symbolsOf(String indexName) {
        if (indexName == null) return List.of();
        List<String> s = indices.get(indexName.toUpperCase());
        return s != null ? List.copyOf(s) : List.of();
    }

    /**
     * Belirtilen endeks adının tanımlı olup olmadığını kontrol eder.
     *
     * @param indexName endeks adı
     * @return {@code true} eğer endeks tanımlıysa
     */
    public boolean containsIndex(String indexName) {
        if (indexName == null) return false;
        return indices.containsKey(indexName.toUpperCase());
    }
}
