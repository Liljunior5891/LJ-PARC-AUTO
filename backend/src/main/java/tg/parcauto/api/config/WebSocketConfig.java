package tg.parcauto.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import tg.parcauto.api.plateforme.tenant.DiffusionEntreprise;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.security.Principal;
import java.util.Arrays;

/**
 * Diffusion temps reel des positions, des alertes GPS et des incidents
 * (chapitres 23, 26 et 33).
 * <p>
 * Chaque entreprise publie sous son propre canal ; un abonnement a un canal qui
 * n'est pas celui de l'entreprise de l'utilisateur est refuse. Seul le serveur
 * diffuse : un navigateur ne peut rien publier, sur aucun canal.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${parcauto.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim)
                        .toArray(String[]::new))
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor entetes = StompHeaderAccessor.wrap(message);
                if (StompCommand.SEND.equals(entetes.getCommand())) {
                    throw new MessageDeliveryException("Envoi refuse : seul le serveur diffuse sur les canaux");
                }
                if (StompCommand.SUBSCRIBE.equals(entetes.getCommand())) {
                    if (!DiffusionEntreprise.appartientA(entetes.getDestination(), entreprise(entetes.getUser()))) {
                        throw new MessageDeliveryException("Abonnement refuse : canal d'une autre entreprise");
                    }
                    // Les canaux diffusent incidents, positions et alertes de la flotte : gestion seule,
                    // avec un compte encore actif (relu en base a chaque abonnement).
                    if (!gestionnaireActif(entetes.getUser())) {
                        throw new MessageDeliveryException("Abonnement refuse : canal reserve a la gestion du parc");
                    }
                }
                return message;
            }
        });
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<tg.parcauto.api.security.UtilisateurDetailsService> comptes;

    private boolean gestionnaireActif(Principal utilisateur) {
        if (!(utilisateur instanceof UsernamePasswordAuthenticationToken jeton
                && jeton.getPrincipal() instanceof UtilisateurPrincipal principal)) {
            return false;
        }
        try {
            var relu = comptes.getObject().loadUserByUsername(principal.getUsername());
            return relu.isEnabled() && relu.getAuthorities().stream().anyMatch(a ->
                    "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_GESTIONNAIRE_PARC".equals(a.getAuthority()));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Long entreprise(Principal utilisateur) {
        if (utilisateur instanceof UsernamePasswordAuthenticationToken jeton
                && jeton.getPrincipal() instanceof UtilisateurPrincipal principal) {
            return principal.getEntrepriseId();
        }
        return null;
    }
}
