package org.mesutormanli.bistadvisor;

import org.mesutormanli.bistadvisor.cli.AdvisorCommands;
import org.mesutormanli.bistadvisor.config.AdvisorMode;
import org.mesutormanli.bistadvisor.config.AnalysisType;
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
 * Uygulamanın {@code @SpringBootApplication} giriş noktası ve iki çalışma modunun seçicisi.
 * <p>
 * Çalışma modları: <strong>web modu</strong> (varsayılan) ve <strong>CLI</strong>
 * (komut satırı) modu. Bilinen komutlar {@code init}, {@code run}, {@code confirm},
 * {@code status} ve {@code train}; CLI modunu tetikleyen imleyici ise {@code --cli}.
 * CLI çağrımında web katmanı hiç kurulmaz ({@code WebApplicationType.NONE}, servlet yok)
 * ve imleyici argüman listesinden çıkarılır. Aksi halde bilinmeyen komut kontrolü yapılır
 * ve normal (web) başlatma olur; seçenek argümanları ({@code --server.port=...} gibi)
 * atlandığı için Spring argümanları web modunu bozmaz.
 * <p>
 * Ayrıca Spring Shell'in komut satırını ele geçirmesi, boş bir
 * {@code ApplicationRunner} bean'i ile engellenir.
 *
 * @see AdvisorCommands
 */
@SpringBootApplication
public class BistAdvisorApplication {

    static final List<String> COMMANDS = List.of("init", "run", "confirm", "status", "train");

    static final String CLI_FLAG = "--cli";

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
     * Spring Shell'in komut satırını ele geçirmesini engelleyen boş
     * {@code ApplicationRunner} bean'ini tanımlar.
     * <p>
     * Spring'in sağladığı runner, bu bean ile OVERRIDE edilir ve boş lambda çalışır.
     *
     * @return hiçbir iş yapmayan {@code ApplicationRunner}
     */
    @Bean
    org.springframework.boot.ApplicationRunner springShellApplicationRunner() {
        return args -> { };
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
    static boolean isCliInvocation(String[] args) {
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
    static String[] stripCliMarker(String[] args) {
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
    private static void checkUnknownCommand(String[] args) {
        if (args.length > 0 && !args[0].startsWith("-") && !COMMANDS.contains(args[0].toLowerCase())) {
            System.err.println("Bilinmeyen komut: " + args[0] + " (komutlar: " + COMMANDS + ")");
            System.exit(1);
        }
    }

    /**
     * CLI komutlarını {@link AdvisorCommands} uygulamasına dağıtan
     * {@code CommandLineRunner} bean'ini tanımlar.
     * <p>
     * İlk argüman bilinen bir komutsa ilgili çağrı yapılır: {@code init} için
     * {@code runInit}; {@code run}, {@code status} ve {@code train} için doğrudan
     * {@link AdvisorCommands} metodları; {@code confirm} için kalan argümanlar liste
     * olarak toplanıp iletilir. Argümansız veya bilinmeyen komutlu çağrılarda sessizce
     * çıkılır.
     *
     * @param commands CLI komutlarının uygulaması
     * @return komutları dağıtacak {@code CommandLineRunner}
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
                default -> { }
            }
        };
    }

    /**
     * {@code init} komutunun argümanlarını ayrıştırır ve çağrıyı yürütür.
     * <p>
     * Desteklenen argümanlar: {@code --budget=} (varsayılan 50000), {@code --mode=},
     * {@code --model=}, {@code --analiz=} (analiz tipi; {@code --analysis=} takma adıyla da
     * alınır) ve {@code --pos=SEMBOL:lot:fiyat,...}. Her hata (geçersiz sayı,
     * eksik alan) ayrı satırda toplanır ve "Hatalar:" başlığı altında yazdırılır;
     * geçerli kısımlarla {@link AdvisorCommands#init} yine de çağrılır.
     *
     * @param commands CLI komutlarının uygulaması
     * @param args     {@code init} komutundan sonraki argümanlar
     */
    private void runInit(AdvisorCommands commands, String[] args) {
        double budget = 50000;
        String mode = null, model = null, analysis = null;
        List<Position> positions = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            try {
                if (a.startsWith("--budget=")) budget = Double.parseDouble(a.substring(9));
                else if (a.startsWith("--mode=")) mode = a.substring(7);
                else if (a.startsWith("--model=")) model = a.substring(8);
                else if (a.startsWith("--analiz=")) analysis = a.substring(9);
                else if (a.startsWith("--analysis=")) analysis = a.substring(11);
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
        commands.init(budget, mode, model, analysis, positions);
    }
}