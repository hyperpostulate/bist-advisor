package org.mesutormanli.bistadvisor.model;

import org.mesutormanli.bistadvisor.features.TechnicalFeatures;

import java.util.List;

/**
 * Geleceğe bakarak AL/TUT/SAT sınıf etiketi üreten etiketleyici.
 *
 * <p>Sınıf etiketleri: {@link #BUY}=0 (AL), {@link #SELL}=1 (SAT), {@link #HOLD}=2 (TUT).
 * Örnek indeksindeki kapanıştan {@code horizon} bar sonrasının kapanışına kadar olan ileriye
 * dönük getiriye göre: getiri &gt; +%5 eşiğinde AL, &lt; &#8722;%5 eşiğinde SAT, eşiğin
 * arasındaki sakin hareketlerde TUT etiketi atanır. Etiketin geleceği gördüğü garanti
 * edilsin diye son {@code horizon} barda (gelecek bar yoksa) etiketsiz örnek kabul edilip
 * TUT döndürülür.
 */
public final class Labeler {

    public static final int BUY = 0;

    public static final int SELL = 1;

    public static final int HOLD = 2;

    /**
     * Kurar: yalnızca statik üyelere sahip sınıfın örneklenmesini engelleyen özel yapıcı.
     */
    private Labeler() {}

    /**
     * Atar: örneğin ileriye dönük getirisine göre AL/TUT/SAT sınıf etiketini belirler.
     *
     * <p>Getiri, örnek barının kapanışı ile {@code horizon} bar sonrasının kapanışı arasındaki
     * oransal değişimdir. Getiri &gt; +%5 ise AL ({@link #BUY}), &lt; &#8722;%5 ise SAT
     * ({@link #SELL}), aksi halde TUT ({@link #HOLD}). {@code sampleIndex + horizon} seri
     * boyutuna ulaşıp geçtiğinde (gelecek bar yoksa) etiketsiz örnek olarak TUT döndürülür.
     *
     * @param bars        fiyat çubukları serisi
     * @param horizon     ileriye bakma dönemi (bar/gün sayısı, ör. varsayılan 20)
     * @param sampleIndex örneğin seri üzerindeki indeksi
     * @return {@link #BUY}, {@link #SELL} veya {@link #HOLD} sınıf etiketi
     */
    public static int labelFor(List<TechnicalFeatures.Bar> bars, int horizon, int sampleIndex) {
        if (sampleIndex + horizon >= bars.size()) return HOLD;
        double now = bars.get(sampleIndex).close();
        double future = bars.get(sampleIndex + horizon).close();
        double ret = (future - now) / now;
        if (ret > 0.05) return BUY;
        if (ret < -0.05) return SELL;
        return HOLD;
    }
}
