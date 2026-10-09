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
 * BIST endeks tanımlarını {@code classpath:bist-indices.properties} dosyasından yükleyen ve
 * endeks adına göre sembol listelerine erişim sağlayan bileşen.
 * <p>
 * Satır biçimi {@code ENDEKS=SEMBOL1,SEMBOL2,...} şeklindedir; boş satırlar ve {@code #} ile
 * başlayan yorum satırları atlanır. Endeks adları ve semboller büyük harfe çevrilerek saklanır,
 * endeksler dosyadaki görünme sırasıyla korunur.
 */
@Component
public class BistIndices {

    @Value("classpath:bist-indices.properties")
    private Resource indicesResource;

    private final Map<String, List<String>> indices = new LinkedHashMap<>();

    /**
     * Endeks tanım dosyasını okur ve endeks → sembol eşlemelerini belleğe yükler.
     * <p>
     * Spring bileşeni oluşturulunca {@code @PostConstruct} ile otomatik çağrılır.
     *
     * @throws IOException sınıf yolu kaynağı açılamaz veya okuma hatası olursa
     * @implNote Boş semboller listeye eklenmez; hiç sembol içermeyen satırlar yok sayılır.
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
     * Tanımlı tüm endeks adlarını döndürür.
     *
     * @return görünme sırasını koruyan, değiştirilemez endeks adı listesi
     */
    public List<String> indexNames() { return List.copyOf(indices.keySet()); }

    /**
     * Endekse ait hisse sembollerini döndürür; arama büyük/küçük harfsizdir.
     *
     * @param indexName endeks adı ({@code null} olabilir)
     * @return endeksin sembol listesi; endeks bilinmiyorsa veya {@code indexName} {@code null} ise boş liste
     */
    public List<String> symbolsOf(String indexName) {
        if (indexName == null) return List.of();
        List<String> s = indices.get(indexName.toUpperCase());
        return s != null ? List.copyOf(s) : List.of();
    }

    /**
     * Endeks adının tanımlı olup olmadığını belirler; arama büyük/küçük harfsizdir.
     *
     * @param indexName endeks adı ({@code null} olabilir)
     * @return endeks tanımlıysa {@code true}; bilinmiyor veya {@code null} ise {@code false}
     */
    public boolean containsIndex(String indexName) {
        if (indexName == null) return false;
        return indices.containsKey(indexName.toUpperCase());
    }
}
