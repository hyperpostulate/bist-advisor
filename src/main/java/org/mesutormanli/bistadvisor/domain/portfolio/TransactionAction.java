package org.mesutormanli.bistadvisor.domain.portfolio;

import java.util.Optional;

/**
 * Uygulanabilir işlem türü: AL (alım) veya SAT (satım).
 * <p>
 * Metin gösterimleri ({@code AL}, {@code SAT}) arayüzlerin ve durum dosyasının
 * konuştuğu etiketlerdir; {@link #parse(String)} bu etiketleri çözer ve
 * tanınmayan girdiler için boş döner.
 */
public enum TransactionAction {
    AL("AL"),
    SAT("SAT");

    private final String label;

    TransactionAction(String label) {
        this.label = label;
    }

    /**
     * İşlemin görünen etiketini döndürür.
     *
     * @return {@code AL} ya da {@code SAT}
     */
    public String label() {
        return label;
    }

    /**
     * Metin etiketten işlem türünü çözer; arama büyük/küçük harfsizdir.
     *
     * @param raw çözümlenecek etiket ({@code null} olabilir)
     * @return eşleşen işlem türü; tanınmayan ya da boş girdide {@link Optional#empty()}
     */
    public static Optional<TransactionAction> parse(String raw) {
        if (raw == null) return Optional.empty();
        for (TransactionAction a : values()) {
            if (a.label.equalsIgnoreCase(raw.trim())) return Optional.of(a);
        }
        return Optional.empty();
    }
}