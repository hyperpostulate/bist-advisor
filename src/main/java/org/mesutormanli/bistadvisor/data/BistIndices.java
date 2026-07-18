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
 * BIST endekslerine gore hisse listesi. bist-indices.properties icinde her satir
 * "ENDKS_ADI=SEMOL1,SEMOL2,..." seklindedir. Kullanici istedigi endeksi secerek
 * o endeksin hisseleri uzerinden analiz yaptirabilir.
 */
@Component
public class BistIndices {

    @Value("classpath:bist-indices.properties")
    private Resource indicesResource;

    private final Map<String, List<String>> indices = new LinkedHashMap<>();

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

    public List<String> indexNames() {
        return List.copyOf(indices.keySet());
    }

    public List<String> symbolsOf(String indexName) {
        List<String> s = indices.get(indexName == null ? null : indexName.toUpperCase());
        return s != null ? List.copyOf(s) : List.of();
    }

    public boolean containsIndex(String indexName) {
        return indices.containsKey(indexName == null ? "" : indexName.toUpperCase());
    }
}
