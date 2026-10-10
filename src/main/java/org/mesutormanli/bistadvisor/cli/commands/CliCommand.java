package org.mesutormanli.bistadvisor.cli.commands;

/**
 * Tek bir CLI komutunun sözleşmesi: komut adı ve argümanlarla yürütmeyi tanımlar.
 * <p>
 * Her somut komut ({@code init}, {@code run}, {@code confirm}, {@code status},
 * {@code train}) bu arayüzü uygular ve çıktısını
 * {@code ConsoleReportRenderer} üzerinden yazar. Dağıtım
 * {@code CliCommandRunner} tarafından {@link #name()} eşleşmesiyle yapılır.
 */
public interface CliCommand {

    /**
     * Komutun komut satırındaki adını döndürür.
     *
     * @return küçük harfli komut adı
     */
    String name();

    /**
     * Komutu tam argüman listesiyle yürütür.
     *
     * @param args komut satırı argümanları (dizinin 0. elemanı komut adıdır)
     */
    void execute(String[] args);
}