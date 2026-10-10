package org.mesutormanli.bistadvisor.cli;

/**
 * {@code confirm} komutunun tek bir işlem argümanının ayrıştırılmış hâli.
 * <p>
 * Argüman biçimi {@code SEMBOL,AL/SAT,lot,fiyat}'tır. Ayrıştırma hataları eski
 * metinlerle {@code error} alanına yazılır: biçim eksikse
 * {@code "Gecersiz format (beklenen: SEMBOL,AL/SAT,lot,fiyat): "}, işlem AL/SAT
 * değilse {@code "Gecersiz islem (...)": }, sayısal alan bozuksa
 * {@code "Gecersiz sayi: "}. Geçerli girdide {@code error} {@code null}'dır.
 *
 * @param raw    ham argüman metni
 * @param symbol hisse sembolü (geçerli girdide)
 * @param action işlem türü, büyük harfle {@code AL}/{@code SAT} (geçerli girdide)
 * @param lots   işlem lot sayısı (geçerli girdide)
 * @param price  işlem fiyatı (geçerli girdide)
 * @param error  ayrıştırma hatası metni; geçerli girdide {@code null}
 */
public record ConfirmArgs(String raw, String symbol, String action,
                           int lots, double price, String error) {

    /**
     * Girdinin geçerli (hatasız) olup olmadığını bildirir.
     *
     * @return ayrıştırma hatası yoksa {@code true}
     */
    public boolean ok() {
        return error == null;
    }

    /**
     * Tek bir işlem argümanını ayrıştırır.
     *
     * @param arg {@code SEMBOL,AL/SAT,lot,fiyat} biçimli işlem metni
     * @return ayrıştırma sonucu; geçerliyse işlem alanları, geçersizse hata metni dolu
     */
    public static ConfirmArgs parse(String arg) {
        String[] p = arg.split(",");
        if (p.length < 4) {
            return new ConfirmArgs(arg, null, null, 0, 0,
                    "Gecersiz format (beklenen: SEMBOL,AL/SAT,lot,fiyat): " + arg);
        }
        String action = p[1].trim().toUpperCase();
        if (!"AL".equals(action) && !"SAT".equals(action)) {
            return new ConfirmArgs(arg, null, null, 0, 0,
                    "Gecersiz islem (" + action + "): " + arg);
        }
        try {
            return new ConfirmArgs(arg, p[0].trim(), action,
                    Integer.parseInt(p[2].trim()), Double.parseDouble(p[3].trim()), null);
        } catch (NumberFormatException e) {
            return new ConfirmArgs(arg, null, null, 0, 0, "Gecersiz sayi: " + arg);
        }
    }
}