package org.mesutormanli.bistadvisor.adapters.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.mesutormanli.bistadvisor.config.AppConfig;
import org.mesutormanli.bistadvisor.domain.portfolio.PortfolioSnapshot;
import org.mesutormanli.bistadvisor.domain.portfolio.Position;
import org.mesutormanli.bistadvisor.ports.PortfolioStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Portföy durumunu {@code state.yaml} dosyasında YAML biçiminde tutan
 * {@link PortfolioStateStore} adaptörü.
 * <p>
 * Serileştirme Jackson ({@code YAMLFactory}, modüller otomatik kayıtlı) ile
 * {@link PortfolioSnapshot} üzerinden yürütülür; dosya alan adları bu kaydın alan
 * adlarıyla birebir aynıdır. Dosya yoksa boş portföyle başlanır; okuma hatasında boş
 * portföyle devam edilip uyarı loglanır. Yazma hatası yalnızca loglanır, çağıran koda
 * yayılmaz. Dönüşlerde pozisyonlar sembole göre sıralanır; böylece durum dosyası
 * deterministik biçimde yazılır.
 */
@Component
public class YamlPortfolioStateStore implements PortfolioStateStore {
    private static final Logger log = LoggerFactory.getLogger(YamlPortfolioStateStore.class);

    private final File stateFile;
    private final ObjectMapper yamlMapper;

    /**
     * Kurar: durum dosyası yolunu uygulama yapılandırmasından alır (Spring bileşeni).
     *
     * @param appConfig durum dosyası yolunu içeren uygulama yapılandırması
     */
    @Autowired
    public YamlPortfolioStateStore(AppConfig appConfig) {
        this(Path.of(appConfig.stateFile()));
    }

    /**
     * Kurar: durum dosyası yolunu doğrudan alır (test/doğrudan kullanım için).
     *
     * @param stateFile durum dosyasının yolu
     */
    public YamlPortfolioStateStore(Path stateFile) {
        this.stateFile = stateFile.toFile();
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.findAndRegisterModules();
    }

    /**
     * Portföy durumunu durum dosyasından okur.
     * <p>
     * Dosya yoksa boş anlık görüntü döner; okuma hatasında boş anlık görüntüyle devam
     * edilip uyarı loglanır. Dönüşte pozisyonlar sembole göre sıralanır.
     *
     * @return diskten yüklenen (veya boş) anlık görüntü
     */
    @Override
    public PortfolioSnapshot load() {
        PortfolioSnapshot s;
        if (!stateFile.exists()) {
            s = new PortfolioSnapshot();
        } else {
            try {
                s = yamlMapper.readValue(stateFile, PortfolioSnapshot.class);
            } catch (IOException e) {
                log.warn("state.yaml okunamadi, bos baslatiliyor: {}", e.getMessage());
                s = new PortfolioSnapshot();
            }
        }
        if (s.positions != null) {
            s.positions.sort(Comparator.comparing(Position::symbol));
        }
        return s;
    }

    /**
     * Portföy durumunu durum dosyasına yazar.
     *
     * @param snapshot yazılacak anlık görüntü
     * @implNote Yazma hatası yalnızca loglanır, çağıran koda yayılmaz.
     */
    @Override
    public void save(PortfolioSnapshot snapshot) {
        try {
            yamlMapper.writeValue(stateFile, snapshot);
        } catch (IOException e) {
            log.error("state.yaml yazilamadi: {}", e.getMessage());
        }
    }
}