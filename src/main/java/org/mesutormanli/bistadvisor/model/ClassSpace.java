package org.mesutormanli.bistadvisor.model;

import java.util.Arrays;

/**
 * Eğitim etiketlerindeki benzersiz sınıfları {@code 0..K-1} sıkı indekslere eşleyen yardımcı.
 * <p>
 * SMILE modelleri olasılık dizisini <em>gözlenen sınıf sayısı</em> kadar bekler
 * (aksi halde {@code IllegalArgumentException} fırlatır). Bu sınıf, eğitim setinde bir
 * sınıf hiç oluşmamışken (ör. yatay piyasada SAT etiketi üretilmemişken) tahminlerin
 * güvenli biçimde yapılabilmesi için gerekli eşlemeyi sağlar:
 * <ul>
 *   <li>{@link #compress(int[])} — orijinal etiketleri sıkı indekslere çevirir (eğitim için)</li>
 *   <li>{@link #label(int)} — sıkı indeksi orijinal sınıf kimliğine geri çevirir (tahmin için)</li>
 * </ul>
 */
public final class ClassSpace {

    /** Orijinal sınıf kimlikleri (artan sırada). */
    private final int[] classIds;

    private ClassSpace(int[] classIds) {
        this.classIds = classIds;
    }

    /**
     * Verilen etiketlerde geçen benzersiz sınıflardan bir {@code ClassSpace} oluşturur.
     *
     * @param labels eğitim etiketleri ({@code int[N]})
     * @return gözlenen sınıfları içeren eşleme
     */
    public static ClassSpace of(int[] labels) {
        return new ClassSpace(Arrays.stream(labels).distinct().sorted().toArray());
    }

    /**
     * Eğitim setinde gözlenen sınıf sayısını döndürür (K).
     *
     * @return sınıf sayısı
     */
    public int size() {
        return classIds.length;
    }

    /**
     * Sıkı indekse karşılık gelen orijinal sınıf kimliğini döndürür.
     *
     * @param index {@code 0..K-1} arası sıkı indeks
     * @return orijinal sınıf kimliği (ör. {@link Labeler#BUY})
     */
    public int label(int index) {
        return classIds[index];
    }

    /**
     * Orijinal etiketleri sıkı indekslere ({@code 0..K-1}) çevirir.
     *
     * @param labels orijinal etiketler ({@code int[N]})
     * @return sıkı indeksler ({@code int[N]})
     */
    public int[] compress(int[] labels) {
        int[] out = new int[labels.length];
        for (int i = 0; i < labels.length; i++) out[i] = indexOf(labels[i]);
        return out;
    }

    private int indexOf(int label) {
        int i = Arrays.binarySearch(classIds, label);
        if (i < 0) throw new IllegalStateException("egitim setinde olmayan sinif: " + label);
        return i;
    }
}