package org.mesutormanli.bistadvisor;

import org.mesutormanli.bistadvisor.cli.AdvisorCommands;
import org.mesutormanli.bistadvisor.config.AdvisorMode;
import org.mesutormanli.bistadvisor.config.ModelType;
import org.mesutormanli.bistadvisor.portfolio.Position;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Uygulamanın giriş noktası.
 * <p>
 * <strong>Çalışma modu seçimi</strong>: CLI modu yalnızca
 * açıkça istenince başlar — ilk argüman bilinen bir komut ({@code init/run/confirm/status/train})
 * ya da {@code --cli} bayrağı varsa. Diğer tüm argümanlar (ör. {@code --spring.profiles.active=x},
 * {@code --server.port=9090}) Spring yapılandırmasıdır ve uygulama Web modunda açılır.
 * Interaktif Spring Shell kabuğu kapalıdır (komutlar {@code @ShellMethod} içermez;
 * CLI girişi {@link AdvisorCommands} üzerinden çalışır).
 */
@SpringBootApplication
public class BistAdvisorApplication {

    /** CLI komut adları. */
    static final List<String> COMMANDS = List.of("init", "run", "confirm", "status", "train");

    /** CLI modunu tetikleyen açık bayrak. */
    static final String CLI_FLAG = "--cli";

    /**
     * Uygulamanın giriş noktası. CLI çağrısıysa Web/Shell'i kapatarak komutu işler;
     * değilse Spring Boot uygulamasını (Web) başlatır.
     *
     * @param args komut satırı argümanları
     */
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(BistAdvisorApplication.class);
        // Spring Shell'in argümanları kabuk komutu olarak yorumlamasını kapat:
        // CLI girişi bizim cliRunner'ımızdan yürür, kabuk (komutsuz) çöküş kaynağıydı.
        app.setDefaultProperties(java.util.Map.of("spring.shell.interactive.enabled", "false"));
        if (isCliInvocation(args)) {
            app.setWebApplicationType(WebApplicationType.NONE);
            app.run(stripCliMarker(args));
        } else {
            checkUnknownCommand(args);
            app.run(args);
        }
    }

    /**
     * Spring Shell'in uygulama argümanlarını kabuk komutu olarak çalıştıran runner'ını
     * devre dışı bırakır: aynı ada sahip bir bean tanımlandığında
     * {@code ShellRunnerAutoConfiguration.springShellApplicationRunner} geri çekilir.
     * Projede {@code @ShellMethod} komutu yoktur; CLI girişi {@code cliRunner}'dan yürür.
     * (Yoksa {@code --server.port=...} gibi Spring argümanları "komut bulunamadı"
     * istisnasıyla başlatmayı öldürür.)
     *
     * @return işlevsiz (no-op) application runner
     */
    @Bean
    org.springframework.boot.ApplicationRunner springShellApplicationRunner() {
        return args -> { /* kasıtlı boş */ };
    }

    /**
     * Çağrının CLI çalıştırması olup olmadığını belirler: {@code --cli} bayrağı varsa ya da
     * ilk bayraksız argüman bilinen bir komutsa CLI'dır. Spring yapılandırma argümanları
     * ({@code --...}) CLI tetiklemez.
     *
     * @param args komut satırı argümanları
     * @return {@code true} eğer CLI modu çalıştırılacaksa
     */
    static boolean isCliInvocation(String[] args) {
        if (Arrays.asList(args).contains(CLI_FLAG)) return true;
        for (String a : args) {
            if (!a.startsWith("-")) return COMMANDS.contains(a.toLowerCase());
        }
        return false;
    }

    /**
     * {@code --cli} işaretleyicisini argüman listesinden çıkarır.
     *
     * @param args ham argümanlar
     * @return işaretsiz argümanlar
     */
    static String[] stripCliMarker(String[] args) {
        return Arrays.stream(args).filter(a -> !CLI_FLAG.equals(a)).toArray(String[]::new);
    }

    /**
     * İlk argüman bilinmeyen bir komut ise kullanıcıyı uyarır ve çıkarsın; aksi halde
     * Spring argümanlarıyla Web modunda devam edilir.
     *
     * @param args komut satırı argümanları
     */
    private static void checkUnknownCommand(String[] args) {
        if (args.length > 0 && !args[0].startsWith("-") && !COMMANDS.contains(args[0].toLowerCase())) {
            System.err.println("Bilinmeyen komut: " + args[0] + " (komutlar: " + COMMANDS + ")");
            System.exit(1);
        }
    }

    /**
     * CLI modunda çalıştırıldığında ilk argümanı komut olarak alır ve
     * {@code AdvisorCommands} üzerinden ilgili metoda yönlendirir. Bilinen komut
     * içermeyen argümanlar Spring yapılandırması sayılır ve yok sayılır.
     *
     * @param commands CLI komutlarını işleyen servis
     * @return CommandLineRunner Spring bean'i
     */
    @Bean
    CommandLineRunner cliRunner(AdvisorCommands commands) {
        return args -> {
            if (args.length == 0) return;
            String cmd = args[0].toLowerCase();
            if (!COMMANDS.contains(cmd)) return;
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
                default -> { /* ulasilamaz */ }
            }
        };
    }

    /**
     * {@code init} komutunu işler: toplam sermaye, mod, model ve portföy pozisyonlarını
     * komut satırı argümanlarından ayrıştırır ve {@code AdvisorCommands.init()}'e
     * yönlendirir.
     *
     * @param commands CLI komutlarını işleyen servis
     * @param args     komut satırı argümanları ({@code --budget=} toplam sermaye, {@code --mode=...},
     *                 {@code --model=...}, {@code --pos=SEMBOL:lot:fiyat,...})
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