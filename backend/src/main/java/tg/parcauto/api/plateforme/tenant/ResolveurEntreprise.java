package tg.parcauto.api.plateforme.tenant;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Fournit a Hibernate l'entreprise de la session qui s'ouvre.
 * <p>
 * Deux valeurs sentinelles completent les identifiants reels : {@link #AUCUNE},
 * qui ne correspond a aucune ligne et rend donc les donnees metier invisibles
 * hors contexte, et {@link #RACINE}, qui leve le filtre pour le mode systeme.
 * Un oubli de contexte aboutit ainsi a « rien », jamais a « tout ».
 */
@Component
public class ResolveurEntreprise implements CurrentTenantIdentifierResolver<Long>, HibernatePropertiesCustomizer {

    public static final Long AUCUNE = -1L;
    public static final Long RACINE = 0L;

    @Override
    public Long resolveCurrentTenantIdentifier() {
        if (ContexteEntreprise.systeme()) {
            return RACINE;
        }
        return ContexteEntreprise.courante().orElse(AUCUNE);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public boolean isRoot(Long tenantId) {
        return RACINE.equals(tenantId);
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
