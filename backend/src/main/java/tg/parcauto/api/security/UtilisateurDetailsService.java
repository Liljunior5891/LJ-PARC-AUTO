package tg.parcauto.api.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.plateforme.entreprise.Entreprise;
import tg.parcauto.api.plateforme.entreprise.EntrepriseRepository;

@Service
@RequiredArgsConstructor
public class UtilisateurDetailsService implements UserDetailsService {

    private final UtilisateurRepository repository;
    private final EntrepriseRepository entrepriseRepository;

    /**
     * Charge le compte et son entreprise. Relu a chaque requete, il rend effective
     * immediatement la suspension d'une entreprise, sans attendre l'expiration des jetons.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Utilisateur utilisateur = repository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable : " + email));
        if (utilisateur.getEntrepriseId() == null) {
            return new UtilisateurPrincipal(utilisateur, null, true);
        }
        Entreprise entreprise = entrepriseRepository.findById(utilisateur.getEntrepriseId()).orElse(null);
        return new UtilisateurPrincipal(utilisateur,
                entreprise == null ? null : entreprise.getNom(),
                entreprise != null && entreprise.active());
    }
}
