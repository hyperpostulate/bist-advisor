package org.mesutormanli.bistadvisor.adapters.cache;

import org.mesutormanli.bistadvisor.domain.market.Bar;

import java.util.ArrayList;
import java.util.List;

/**
 * Fiyat barlarını CSV satırlarına (ve geri) dönüştüren saf codec.
 * <p>
 * Satır biçimi {@code tarih,kapanış,hacim}'dir. Çözümleme tarafı hem 3+ kolonlu
 * {@code tarih,kapanış,hacim} hem de 6 kolonlu OHLCV
 * {@code tarih,aç,yüksek,düşük,kapanış,hacim} biçimini destekler: 5+ kolonda kapanış
 * 5. alandan (indeks 4), 6+ kolonda hacim 6. alandan (indeks 5) okunur. Tüm üyeler
 * statiktir; saf dönüşüm yapar, I/O içermez.
 */
public final class BarCsvCodec {

    private BarCsvCodec() {}

    /**
     * Bir barı CSV satırına kodlar.
     *
     * @param bar kodlanacak bar
     * @return {@code tarih,kapanış,hacim} biçiminde satır (hacim tam sayıya yuvarlanır)
     */
    public static String encode(Bar bar) {
        return bar.date() + "," + bar.close() + "," + (long) bar.volume();
    }

    /**
     * Bar listesini CSV satırlarına kodlar.
     *
     * @param bars kodlanacak barlar
     * @return {@code tarih,kapanış,hacim} biçiminde satırlar
     */
    public static List<String> encode(List<Bar> bars) {
        List<String> lines = new ArrayList<>(bars.size());
        for (Bar b : bars) lines.add(encode(b));
        return lines;
    }

    /**
     * CSV satırlarını bar nesnelerine çözer.
     *
     * @param csvLines ayrıştırılacak CSV satırları
     * @return oluşturulan bar listesi
     * @implNote Sayısal olmayan alan içeren satırlar sessizce atlanır.
     */
    public static List<Bar> decode(List<String> csvLines) {
        List<Bar> bars = new ArrayList<>();
        for (String line : csvLines) {
            String[] p = line.split(",");
            if (p.length < 3) continue;
            try {
                double close = p.length >= 5 ? Double.parseDouble(p[4]) : Double.parseDouble(p[1]);
                double vol = p.length >= 6 ? Double.parseDouble(p[5]) : Double.parseDouble(p[2]);
                bars.add(new Bar(p[0], close, vol));
            } catch (NumberFormatException ignored) {}
        }
        return bars;
    }
}