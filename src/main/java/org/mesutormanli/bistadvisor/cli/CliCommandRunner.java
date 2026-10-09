package org.mesutormanli.bistadvisor.cli;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * CLI komutlarını {@link AdvisorCommands} uygulamasına dağıtan {@code CommandLineRunner}.
 * <p>
 * İlk argüman bilinen bir komutsa ilgili çağrı yapılır: {@code init} için argümanlar
 * {@link InitArgs} ile ayrıştırılır; {@code run}, {@code status} ve {@code train} için
 * doğrudan {@link AdvisorCommands} metodları; {@code confirm} için kalan argümanlar
 * liste olarak toplanıp iletilir. Argümansız veya bilinmeyen komutlu çağrılarda
 * sessizce çıkılır (web modunda runner sessiz geçer).
 *
 * @see AdvisorCommands
 */
@Component
public class CliCommandRunner implements CommandLineRunner {

    private final AdvisorCommands commands;

    public CliCommandRunner(AdvisorCommands commands) {
        this.commands = commands;
    }

    /**
     * Gelen argümanlardaki komutu tanır ve yürütür.
     *
     * @param args komut satırı argümanları ({@code --cli} imleyicisi düşürülmüş)
     */
    @Override
    public void run(String... args) {
        if (args.length == 0) return;
        String cmd = args[0].toLowerCase();
        if (!CliMode.COMMANDS.contains(cmd)) return;
        switch (cmd) {
            case "init" -> runInit(args);
            case "run" -> commands.run();
            case "confirm" -> {
                List<String> tx = new ArrayList<>();
                for (int i = 1; i < args.length; i++) tx.add(args[i]);
                commands.confirm(tx);
            }
            case "status" -> commands.status();
            case "train" -> commands.train();
            default -> { }
        }
    }

    /**
     * {@code init} komutunu ayrıştırır, hataları yazdırır ve çağrıyı yürütür.
     * <p>
     * Hatalar "Hatalar:" başlığı altında yazdırılır; geçerli kısımlarla
     * {@link AdvisorCommands#init} yine de çağrılır.
     *
     * @param args {@code init} komutu ve argümanları
     */
    private void runInit(String[] args) {
        InitArgs init = InitArgs.parse(args);
        if (!init.errors().isEmpty()) {
            System.out.println("Hatalar:");
            init.errors().forEach(System.out::println);
        }
        commands.init(init.budget(), init.mode(), init.model(), init.analysis(), init.positions());
    }
}