package org.mesutormanli.bistadvisor.model;

import java.util.Arrays;

/**
 * Eğitim etiketlerinden tekrarsız, artan sırada sınıf kimliği tablosu üreten ve bu tablo
 * üzerinden sınıf etiketlerini {@code 0..k-1} arası kesintisiz indekslere eşleyen yardımcı sınıf.
 *
 * <p>SMILE modelleri tahminde olasılık vektörünün boyutunu eğitimdeki sınıf sayısı kadar bekler;
 * eğitim setinde bir sınıf hiç oluşmamışsa (ör. yatay piyasada SAT etiketi üretilmemişse) ham
 * sınıf kimlikleriyle çalışmak hata üretir. Bu sınıf bu tuzağı çözer: eğitimde gözlenen sınıfları
 * sıfırdan başlayan kesintisiz indekslere {@link #compress(int[])} ile sıkıştırır, tahmin sırasında
 * bulunan indeksi {@link #label(int)} ile geri açar. Eğitimde olmayan bir sınıf için istisna
 * fırlattığından, stratejiler eksik sınıf durumunda istisna üretmeden yalnızca gözlenen sınıflar
 * arasında tahmin yapabilir.
 *
 * @implNote Sınıf kimlikleri ikili arama ({@code Arrays.binarySearch}) ile bulunduğu için tablonun
 *           artan sırada tutulması şarttır; tablo {@link #of(int[])} içinde sıralanarak kurulur.
 */
public final class ClassSpace {

    private final int[] classIds;

    /**
     * Kurar: sınıf kimlikleri hazır bir tabloyla eşleme oluşturur.
     *
     * @param classIds artan sırada tekrarsız sınıf kimlikleri dizisi
     */
    private ClassSpace(int[] classIds) {
        this.classIds = classIds;
    }

    /**
     * Oluşturur: eğitim etiketlerinde geçen tekrarsız sınıflardan artan sırada bir tablo kurar.
     *
     * @param labels eğitim etiketleri ({@code int[N]}, ör. AL=0, SAT=1, TUT=2)
     * @return gözlenen sınıfları içeren eşleme
     */
    public static ClassSpace of(int[] labels) {
        return new ClassSpace(Arrays.stream(labels).distinct().sorted().toArray());
    }

    /**
     * Döndürür: eğitimde gözlenen sınıf sayısını (k) verir.
     *
     * @return sınıf sayısı
     */
    public int size() {
        return classIds.length;
    }

    /**
     * Geri açar: sıkı indeksi orijinal sınıf kimliğine çevirir (tahmin sonrası kullanım).
     *
     * @param index {@code 0..k-1} arası kesintisiz sınıf indeksi
     * @return orijinal sınıf etiketi (ör. {@link Labeler#BUY})
     * @throws ArrayIndexOutOfBoundsException indeks tablo aralığının dışındaysa
     */
    public int label(int index) {
        return classIds[index];
    }

    /**
     * Sıkıştırır: orijinal sınıf etiketlerini {@code 0..k-1} arası kesintisiz indekslere eşler
     * (eğitim öncesi kullanım).
     *
     * @param labels orijinal sınıf etiketleri ({@code int[N]})
     * @return sıkı indeksler ({@code int[N]})
     * @throws IllegalStateException etiketlerde eğitim tablosunda olmayan bir sınıf varsa
     */
    public int[] compress(int[] labels) {
        int[] out = new int[labels.length];
        for (int i = 0; i < labels.length; i++) out[i] = indexOf(labels[i]);
        return out;
    }

    /**
     * Bulur: sınıf kimliğinin tablodaki sırasını (sıkı indeksini) ikili aramayla hesaplar.
     *
     * @param label aranan sınıf kimliği
     * @return sıkı indeks ({@code 0..k-1})
     * @throws IllegalStateException sınıf eğitim setinde yoksa
     */
    private int indexOf(int label) {
        int i = Arrays.binarySearch(classIds, label);
        if (i < 0) throw new IllegalStateException("egitim setinde olmayan sinif: " + label);
        return i;
    }
}