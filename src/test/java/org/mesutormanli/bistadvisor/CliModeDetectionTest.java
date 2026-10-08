package org.mesutormanli.bistadvisor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CLI/Web çalışma modu ayrımının testleri: CLI yalnızca
 * bilinen komut ya da {@code --cli} bayrağı ile başlamalı; Spring yapılandırma
 * argümanları Web modunu bozmamalı.
 */
class CliModeDetectionTest {

    @Test
    void bosArgumanWebModudur() {
        assertFalse(BistAdvisorApplication.isCliInvocation(new String[]{}));
    }

    @Test
    void bilinenKomutlarCliBaslatir() {
        for (String cmd : BistAdvisorApplication.COMMANDS) {
            assertTrue(BistAdvisorApplication.isCliInvocation(new String[]{cmd}), cmd);
            assertTrue(BistAdvisorApplication.isCliInvocation(new String[]{cmd, "--budget=1"}), cmd);
        }
    }

    @Test
    void cliBayragiCliBaslatir() {
        assertTrue(BistAdvisorApplication.isCliInvocation(new String[]{"--cli", "train"}));
    }

    @Test
    void springArgumanlariWebModunuBozmaz() {
        assertFalse(BistAdvisorApplication.isCliInvocation(new String[]{"--spring.profiles.active=prod"}));
        assertFalse(BistAdvisorApplication.isCliInvocation(new String[]{"--server.port=9090"}));
    }

    @Test
    void cliBayragiArgumanlardanCikarilir() {
        assertArrayEquals(new String[]{"status"},
                BistAdvisorApplication.stripCliMarker(new String[]{"--cli", "status"}));
        assertArrayEquals(new String[]{"init", "--budget=50000"},
                BistAdvisorApplication.stripCliMarker(new String[]{"init", "--budget=50000"}));
    }
}