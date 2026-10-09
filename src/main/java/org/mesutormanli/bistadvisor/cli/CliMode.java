package org.mesutormanli.bistadvisor.cli;

import java.util.Arrays;
import java.util.List;

/**
 * Komut satırı çağrımının CLI mı yoksa web modu mu olduğunu ayırt eden yardımcılar.
 * <p>
 * Bilinen komutlar {@code init}, {@code run}, {@code confirm}, {@code status} ve
 * {@code train}; CLI modunu tetikleyen imleyici ise {@code --cli}. Seçenek argümanlar
 * ({@code -} ile başlayanlar) atlandığı için Spring argümanları web modunu bozmaz.
 *
 * @see BistAdvisorApplication
 */
public final class CliMode {

    /** Bilinen CLI komutlarının adları. */
    public static final List<String> COMMANDS = List.of("init", "run", "confirm", "status", "train");

    /** CLI modunu zorlayan imleyici argümanı. */
    public static final String CLI_FLAG = "--cli";

    private CliMode() {
    }

    /**
     * Argümanların bir CLI çağrımı olup olmadığını belirler.
     * <p>
     * {@code --cli} imleyicisi varsa doğrudan {@code true} döner. Yoksa ilk seçenek
     * olmayan ({@code -} ile başlamayan) argümanın bilinen komutlardan biri olup olmadığına
     * bakılır (büyük/küçük harfsiz). Seçenek argümanlar atlandığı için Spring argümanları
     * web modunu bozmaz.
     *
     * @param args komut satırı argümanları
     * @return CLI çağrımıysa {@code true}, web modu çağrımıysa {@code false}
     */
    public static boolean isCliInvocation(String[] args) {
        if (Arrays.asList(args).contains(CLI_FLAG)) return true;
        for (String a : args) {
            if (!a.startsWith("-")) return COMMANDS.contains(a.toLowerCase());
        }
        return false;
    }

    /**
     * Argüman listesinden yalnızca {@code --cli} imleyicisini düşürür.
     *
     * @param args ham argümanlar
     * @return imleyici çıkarılmış yeni argüman dizisi
     * @implNote Diğer argümanların sırası ve içeriği aynen korunur.
     */
    public static String[] stripCliMarker(String[] args) {
        return Arrays.stream(args).filter(a -> !CLI_FLAG.equals(a)).toArray(String[]::new);
    }

    /**
     * İlk argümanın bilinmeyen bir komut olmadığını kontrol eder.
     * <p>
     * İlk argüman {@code -} ile başlamıyorsa ve bilinen komutlar arasında değilse hata
     * metni standart hata akışına (stderr) yazılır ve {@code System.exit(1)} ile çıkılır.
     *
     * @param args komut satırı argümanları
     */
    public static void checkUnknownCommand(String[] args) {
        if (args.length > 0 && !args[0].startsWith("-") && !COMMANDS.contains(args[0].toLowerCase())) {
            System.err.println("Bilinmeyen komut: " + args[0] + " (komutlar: " + COMMANDS + ")");
            System.exit(1);
        }
    }
}