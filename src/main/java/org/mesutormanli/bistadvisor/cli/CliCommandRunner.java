package org.mesutormanli.bistadvisor.cli;

import org.mesutormanli.bistadvisor.cli.commands.CliCommand;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * CLI komutlarını {@link CliCommand} uygulamalarına dağıtan {@code CommandLineRunner}.
 * <p>
 * İlk argüman bilinen bir komutsa adı eşleşen komut yürütülür (bkz.
 * {@link CliCommand#name()}). Argümansız veya bilinmeyen komutlu çağrılarda sessizce
 * çıkılır (web modunda runner sessiz geçer); bilinmeyen komut denetimi başlatma
 * tarafında {@link CliMode#unknownCommandError} ile yapılır.
 */
@Component
public class CliCommandRunner implements CommandLineRunner {

    private final List<CliCommand> commands;

    /**
     * Kurar: komut uygulamalarını enjekte eder.
     *
     * @param commands yürütülebilecek CLI komutları
     */
    public CliCommandRunner(List<CliCommand> commands) {
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
        for (CliCommand command : commands) {
            if (command.name().equals(cmd)) {
                command.execute(args);
                return;
            }
        }
    }
}