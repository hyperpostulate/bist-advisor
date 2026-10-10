package org.mesutormanli.bistadvisor.ports;

import org.mesutormanli.bistadvisor.domain.portfolio.PortfolioSnapshot;

/**
 * Portföy durumunun kalıcı deposuna erişimin dışa açık sözleşmesi (port).
 * <p>
 * Uygulama servisleri portföy mutasyonlarını bu arayüz üzerinden kalıcılaştırır;
 * somut depo (ör. {@code state.yaml}) adaptör katmanında bu sözleşmeyi uygular.
 * Serileştirme biçimi {@link PortfolioSnapshot} alanlarıyla birebir aynıdır.
 */
public interface PortfolioStateStore {

    /**
     * Kayıtlı portföy durumunu yükler.
     *
     * @return diskten okunan anlık görüntü; kayıt yoksa veya okuma hatasında boş varsayılanlar
     */
    PortfolioSnapshot load();

    /**
     * Portföy durumunu kalıcı depoya yazar.
     *
     * @param snapshot yazılacak anlık görüntü
     */
    void save(PortfolioSnapshot snapshot);
}