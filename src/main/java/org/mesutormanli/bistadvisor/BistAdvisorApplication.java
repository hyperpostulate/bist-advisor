package org.mesutormanli.bistadvisor;

import org.mesutormanli.bistadvisor.cli.AdvisorCommands;
import org.mesutormanli.bistadvisor.config.AdvisorMode;
import org.mesutormanli.bistadvisor.config.ModelType;
import org.mesutormanli.bistadvisor.portfolio.Position;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
public class BistAdvisorApplication {

    /**
     * Uygulamanın giriş noktası. Hiç argüman verilmezse Web modunda (Spring Boot web)
     * çalışır; argüman varsa CLI modunda çalışarak Spring Shell'i devre dışı bırakır
     * ve komut satırından {code init}, {code run}, {code confirm}, {code status},
     * {code train} komutlarını işletir.
     *
     * @param args komut satırı argümanları
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            SpringApplication.run(BistAdvisorApplication.class, args);
        } else {
            SpringApplication app = new SpringApplication(BistAdvisorApplication.class);
            app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
            app.setDefaultProperties(java.util.Map.of("spring.shell.enabled", "false"));
            app.run(args);
        }
    }

    /**
     * CLI modunda çalıştırıldığında ilk argümanı komut olarak alır ve
     * {code AdvisorCommands} üzerinden ilgili metoda yönlendirir.
     *
     * @param commands CLI komutlarını işleyen servis
     * @return CommandLineRunner Spring bean'i
     */
    @Bean
    CommandLineRunner cliRunner(AdvisorCommands commands) {
        return args -> {
            if (args.length == 0) return;
            String cmd = args[0];
            switch (cmd) {
                case "init" -> runInit(commands, args);
                case "run" -> commands.run();
                case "confirm" -> {
                    List<String> tx = new ArrayList<>();
                    for (int i = 1; i < args.length; i++) tx.add(args[i]);
                    commands.confirm(tx);
                }
                case "status" -> commands.status();
                case "train" -> commands.train();
                default -> System.out.println("Bilinmeyen komut: " + cmd);
            }
        };
    }

    /**
     * {code init} komutunu işler: toplam sermaye, mod, model ve portföy pozisyonlarını
     * komut satırı argümanlarından ayrıştırır ve {code AdvisorCommands.init()}'e
     * yönlendirir.
     *
     * @param commands CLI komutlarını işleyen servis
     * @param args     komut satırı argümanları ({code --budget=} toplam sermaye, {code --mode=...},
     *                 {code --model=...}, {code --pos=SEMBOL:lot:fiyat,...})
     */
    private void runInit(AdvisorCommands commands, String[] args) {
        double budget = 50000;
        String mode = null, model = null;
        List<Position> positions = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            try {
                if (a.startsWith("--budget=")) budget = Double.parseDouble(a.substring(9));
                else if (a.startsWith("--mode=")) mode = a.substring(7);
                else if (a.startsWith("--model=")) model = a.substring(8);
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
        if (!errors.isEmpty()) {
            System.out.println("Hatalar:");
            errors.forEach(System.out::println);
        }
        commands.init(budget, mode, model, positions);
    }
}
