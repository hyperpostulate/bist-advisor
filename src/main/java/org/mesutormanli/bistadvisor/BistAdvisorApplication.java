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

/**
 * BIST-30 Portföy Danışmanı.
 * Argüman verilmezse web sunucusu (http://localhost:8080) başlar.
 * Argüman verilirse CLI komutu çalışır.
 */
@SpringBootApplication
public class BistAdvisorApplication {

    public static void main(String[] args) {
        if (args.length == 0) {
            SpringApplication.run(BistAdvisorApplication.class, args);
        } else {
            // CLI modu: web ve interaktif shell'i devre disi birak, sadece komutu calistir
            SpringApplication app = new SpringApplication(BistAdvisorApplication.class);
            app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
            app.setDefaultProperties(java.util.Map.of(
                    "spring.shell.enabled", "false"));
            app.run(args);
        }
    }

    @Bean
    CommandLineRunner cliRunner(AdvisorCommands commands) {
        return args -> {
            if (args.length == 0) return; // web modu
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
                        if (kv.length == 3) {
                            try {
                                positions.add(new Position(kv[0].toUpperCase(), Integer.parseInt(kv[1]), Double.parseDouble(kv[2])));
                            } catch (NumberFormatException e) {
                                errors.add("Gecersiz pozisyon: " + p);
                            }
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
