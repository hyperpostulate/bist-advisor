package org.mesutormanli.bistadvisor.cli;

import org.mesutormanli.bistadvisor.portfolio.Position;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code init} komutunun ayrıştırılmış argümanları.
 * <p>
 * Desteklenen argümanlar: {@code --budget=} (varsayılan 50000), {@code --mode=},
 * {@code --model=}, {@code --analiz=} (analiz tipi; {@code --analysis=} takma adıyla da
 * alınır) ve {@code --pos=SEMBOL:lot:fiyat,...}. Her hata (geçersiz sayı, eksik alan)
 * ayrı satırda {@code errors} listesinde toplanır; geçerli kısımlar korunur.
 *
 * @param budget   toplam sermaye katkısı
 * @param mode     yatırım modu (geçerliyse; yoksa {@code null})
 * @param model    model tipi (geçerliyse; yoksa {@code null})
 * @param analysis analiz tipi (geçerliyse; yoksa {@code null})
 * @param positions pozisyon listesi
 * @param errors   ayrıştırma hataları (her hata tek satır)
 * @see AdvisorCommands#init
 */
public record InitArgs(
        double budget,
        String mode,
        String model,
        String analysis,
        List<Position> positions,
        List<String> errors) {

    /**
     * {@code init} komutundan sonraki argümanları ayrıştırır.
     *
     * @param args {@code init} komutundan sonraki argümanlar
     * @return ayrıştırılmış argümanlar ve toplanan hatalar
     */
    public static InitArgs parse(String[] args) {
        double budget = 50000;
        String mode = null, model = null, analysis = null;
        List<Position> positions = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            try {
                if (a.startsWith("--budget=")) budget = Double.parseDouble(a.substring(9));
                else if (a.startsWith("--mode=")) mode = a.substring(7);
                else if (a.startsWith("--model=")) model = a.substring(8);
                else if (a.startsWith("--analiz=")) analysis = a.substring(9);
                else if (a.startsWith("--analysis=")) analysis = a.substring(11);
                else if (a.startsWith("--pos=")) {
                    for (String p : a.substring(6).split(",")) {
                        String[] kv = p.split(":");
                        if (kv.length >= 3) {
                            try {
                                positions.add(new Position(kv[0], Integer.parseInt(kv[1]), Double.parseDouble(kv[2])));
                            } catch (NumberFormatException e) {
                                errors.add("Gecersiz pozisyon: " + p);
                            }
                        } else {
                            errors.add("Eksik alan (beklenen: SEMBOL:lot:fiyat): " + p);
                        }
                    }
                }
            } catch (NumberFormatException e) {
                errors.add("Gecersiz arguman: " + a);
            }
        }
        return new InitArgs(budget, mode, model, analysis, positions, errors);
    }
}