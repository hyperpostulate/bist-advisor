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
     * Uygulama giris noktasi. Args yoksa web modu (port 8080), args varsa CLI modu.
     * CLI modunda Spring Shell ve web sunucusu devre disi birakilir, komut
     * dogrudan CommandLineRunner uzerinden calistirilir.
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

    /** CLI komutlarini args[0]'a gore yonlendirir. */
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

    /** --budget, --mode, --model, --pos parametrelerini cozumler ve commands.init'e gecirir. */
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
