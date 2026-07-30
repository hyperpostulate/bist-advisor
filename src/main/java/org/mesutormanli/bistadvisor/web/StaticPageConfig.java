package org.mesutormanli.bistadvisor.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Statik web kaynaklarını (HTML, CSS, JS) {@code classpath:/static/} dizininden
 * sunacak şekilde Spring MVC'yi yapılandırır.
 * <p>
 * Ana sayfa için {@code index.html} dosyasını kök URL'de sunar.
 */
@Configuration
public class StaticPageConfig implements WebMvcConfigurer {
    /**
     * Statik kaynak işleyicisini kaydeder: tüm {@code /**} yollarını
     * {@code classpath:/static/} dizinine yönlendirir.
     *
     * @param registry Spring MVC kaynak işleyici kaydı
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }
}
