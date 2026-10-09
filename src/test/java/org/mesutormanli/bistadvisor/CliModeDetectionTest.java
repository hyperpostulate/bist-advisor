package org.mesutormanli.bistadvisor;

import org.junit.jupiter.api.Test;
import org.mesutormanli.bistadvisor.cli.CliMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CliMode} komut satırı/web modu ayrımının testleri.
 * <p>
 * Hangi argüman kümesinin uygulamayı web sunucusu olarak, hangisinin CLI
 * ({@code WebApplicationType.NONE}) olarak başlatacağını ve {@code --cli}
 * imleyicisinin argümanlardan nasıl ayıklanacağını güvence altına alır.
 */
class CliModeDetectionTest {

    /**
     * Argümansız çalıştırmanın web moduna karşılık gelmesi.
     * <p>
     * Senaryo: uygulama hiç argümanla başlatılmaz. Beklenen davranış:
     * {@code isCliInvocation} false döner ve Spring Boot web uygulaması olarak
     * açılır.
     */
    @Test
    void bosArgumanWebModudur() {
        assertFalse(CliMode.isCliInvocation(new String[]{}));
    }

    /**
     * Bilinen komutların tek başına veya ek argümanlarla CLI başlatması.
     * <p>
     * Senaryo: {@code init}, {@code run}, {@code confirm}, {@code status} ve
     * {@code train} komutlarının her biri önce tek başına, sonra
     * {@code --budget=1} gibi ek argümanlarla denenir. Beklenen davranış: her iki
     * biçimde de {@code isCliInvocation} true döner ve CLI çalıştırıcı devreye girer.
     */
    @Test
    void bilinenKomutlarCliBaslatir() {
        for (String cmd : CliMode.COMMANDS) {
            assertTrue(CliMode.isCliInvocation(new String[]{cmd}), cmd);
            assertTrue(CliMode.isCliInvocation(new String[]{cmd, "--budget=1"}), cmd);
        }
    }

    /**
     * {@code --cli} imleyicisinin her koşulda CLI başlatması.
     * <p>
     * Senaryo: argümanlar {@code --cli train} biçimindedir. Beklenen davranış:
     * imleyici tek başına CLI başlatmayı tetikler ve {@code isCliInvocation} true
     * döner.
     */
    @Test
    void cliBayragiCliBaslatir() {
        assertTrue(CliMode.isCliInvocation(new String[]{"--cli", "train"}));
    }

    /**
     * Spring seçeneklerinin web modunu bozmaması.
     * <p>
     * Senaryo: yalnızca {@code --spring.profiles.active=prod} veya
     * {@code --server.port=9090} argümanı verilir. Beklenen davranış: tireyle
     * başlayan bu argümanlar komut adayı sayılmaz ve {@code isCliInvocation} false
     * döner; uygulama web modunda kalır.
     */
    @Test
    void springArgumanlariWebModunuBozmaz() {
        assertFalse(CliMode.isCliInvocation(new String[]{"--spring.profiles.active=prod"}));
        assertFalse(CliMode.isCliInvocation(new String[]{"--server.port=9090"}));
    }

    /**
     * {@code --cli} imleyicisinin argüman listesinden düşürülmesi.
     * <p>
     * Senaryo: imleyici içeren {@code [--cli, status]} ve imleyici içermeyen
     * {@code [init, --budget=50000]} listeleri denenir. Beklenen davranış:
     * {@code stripCliMarker} yalnız imleyiciyi siler — ilk liste
     * {@code [status]}, ikincisi ise aynen {@code [init, --budget=50000]} olarak
     * döner.
     */
    @Test
    void cliBayragiArgumanlardanCikarilir() {
        assertArrayEquals(new String[]{"status"},
                CliMode.stripCliMarker(new String[]{"--cli", "status"}));
        assertArrayEquals(new String[]{"init", "--budget=50000"},
                CliMode.stripCliMarker(new String[]{"init", "--budget=50000"}));
    }
}