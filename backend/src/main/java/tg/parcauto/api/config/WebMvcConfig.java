package tg.parcauto.api.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tg.parcauto.api.marketplace.ContexteBoutiqueInterceptor;
import tg.parcauto.api.plateforme.module.ControleModuleInterceptor;
import tg.parcauto.api.pointdevente.restauration.qr.ContexteTableInterceptor;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final ControleModuleInterceptor controleModule;
    private final ContexteTableInterceptor contexteTable;
    private final ContexteBoutiqueInterceptor contexteBoutique;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // L'entreprise d'un client sans compte vient du jeton de sa table, avant tout controle de module.
        registry.addInterceptor(contexteTable).addPathPatterns("/api/public/qr/**").order(0);
        // Boutique en ligne et suivi de commande : l'entreprise vient de l'adresse ou du jeton de suivi.
        registry.addInterceptor(contexteBoutique)
                .addPathPatterns("/api/public/boutiques/**", "/api/public/suivi/**").order(0);
        registry.addInterceptor(controleModule).addPathPatterns("/api/**").order(1);
    }
}
