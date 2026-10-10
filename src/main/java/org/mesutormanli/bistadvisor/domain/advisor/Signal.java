package org.mesutormanli.bistadvisor.domain.advisor;

import java.util.Optional;

/**
 * Öneri sinyali: AL, TUT veya SAT.
 * <p>
 * Metin etiketler ({@code AL}, {@code TUT}, {@code SAT}) arayüzlerin ve raporların
 * konuştuğu gösterimdir. İşlem (emir) anlamına gelmez; uygulanabilir emirler
 * {@code TransactionAction} ile taşınır.
 */
public enum Signal {
    AL("AL"),
    TUT("TUT"),
    SAT("SAT");

    private final String label;

    Signal(String label) {
        this.label = label;
    }

    /**
     * Sinyalin görünen etiketini döndürür.
     *
     * @return {@code AL}, {@code TUT} ya da {@code SAT}
     */
    public String label() {
        return label;
    }

    /**
     * Metin etiketten sinyal çözer; arama büyük/küçük harfsizdir.
     *
     * @param raw çözümlenecek etiket ({@code null} olabilir)
     * @return eşleşen sinyal; tanınmayan girdide {@link Optional#empty()}
     */
    public static Optional<Signal> parse(String raw) {
        if (raw == null) return Optional.empty();
        for (Signal s : values()) {
            if (s.label.equalsIgnoreCase(raw.trim())) return Optional.of(s);
        }
        return Optional.empty();
    }
}