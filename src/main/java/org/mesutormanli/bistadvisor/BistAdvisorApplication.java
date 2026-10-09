package org.mesutormanli.bistadvisor;

import org.mesutormanli.bistadvisor.cli.CliMode;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Map;

/**
 * Uygulamanın {@code @SpringBootApplication} giriş noktası ve iki çalışma modunun seçicisi.
 * <p>
 * Çalışma modları: <strong>web modu</strong> (varsayılan) ve <strong>CLI</strong>
 * (komut satırı) modu. Ayrımın kendisi {@link CliMode}'da, komut dağıtımı ise
 * {@link CommandLineRunner} olarak {@code CliCommandRunner} içindedir; CLI çağrımında
 * web katmanı hiç kurulmaz ({@code WebApplicationType.NONE}, servlet yok).
 * <p>
 * Ayrıca Spring Shell'in komut satırını ele geçirmesi, boş bir
 * {@code ApplicationRunner} bean'i ile engellenir.
 *
 * @see CliMode
 */
@SpringBootApplication
public class BistAdvisorApplication {

    /**
     * Uygulamayı başlatır ve çağrım biçimine göre CLI veya web modunu seçer.
     * <p>
     * Spring Shell'in interaktif kipten otomatik başlamasını kapatmak için
     * {@code spring.shell.interactive.enabled=false} varsayılan özelliği atanır.
     * CLI çağrımında {@code WebApplicationType.NONE} ile servlet kurulmaz ve {@code --cli}
     * imleyicisi argümanlardan düşürülür; aksi halde bilinmeyen komut kontrolü yapılarak
     * normal (web) başlatma yapılır.
     *
     * @param args komut satırı argümanları
     * @throws IllegalStateException Spring bağlamı başlatılamazsa
     */
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(BistAdvisorApplication.class);
        app.setDefaultProperties(Map.of("spring.shell.interactive.enabled", "false"));
        if (CliMode.isCliInvocation(args)) {
            app.setWebApplicationType(WebApplicationType.NONE);
            app.run(CliMode.stripCliMarker(args));
        } else {
            CliMode.checkUnknownCommand(args);
            app.run(args);
        }
    }

    /**
     * Spring Shell'in komut satırını ele geçirmesini engelleyen boş
     * {@code ApplicationRunner} bean'ini tanımlar.
     * <p>
     * Spring'in sağladığı runner, bu bean ile OVERRIDE edilir ve boş lambda çalışır.
     *
     * @return hiçbir iş yapmayan {@code ApplicationRunner}
     */
    @Bean
    ApplicationRunner springShellApplicationRunner() {
        return args -> { };
    }
}