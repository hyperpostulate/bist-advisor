package org.mesutormanli.bistadvisor.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Tek sayfalık arayüzün ve statik kaynakların kök yoldan sunulmasını sağlayan web yapılandırması.
 * <p>
 * {@code classpath:/static/} içeriğini {@code /**} yolunda servis eder; böylece tek
 * sayfalık arayüz {@code static/index.html} ve diğer statikler kökten erişilebilir olur.
 * {@code /api} REST uç noktalarına dokunulmaz.
 */
@Configuration
public class StaticPageConfig implements WebMvcConfigurer {
    /**
     * Statik kaynak yönlendirmelerini kaydeder.
     * <p>
     * {@code classpath:/static/} içeriği {@code /**} yolundan sunulur; bu, tek sayfalık
     * arayüzün ve statiklerin kökten servis edilmesini sağlar.
     *
     * @param registry statik kaynak işleyicilerinin kaydedildiği kayıt defteri
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }
}
