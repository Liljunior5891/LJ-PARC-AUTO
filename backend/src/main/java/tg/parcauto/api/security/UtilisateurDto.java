package tg.parcauto.api.security;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Echanges de l'ecran de gestion des utilisateurs. */
public final class UtilisateurDto {

    /** Longueur minimale imposee aux mots de passe crees ou reinitialises. */
    public static final int LONGUEUR_MOT_DE_PASSE = 8;

    private UtilisateurDto() {
    }

    public record Reponse(
            Long id,
            String email,
            String nom,
            String prenom,
            String nomComplet,
            String telephone,
            boolean actif,
            Set<Role> roles,
            List<String> rolesLibelles,
            Instant creeLe,
            Instant modifieLe,
            boolean pinDefini) {
    }

    /** Code PIN de 4 a 6 chiffres ; nul pour l'effacer (le caissier en creera un nouveau). */
    public record DefinitionPin(
            @jakarta.validation.constraints.Pattern(regexp = "^\\d{4,6}$", message = "4 a 6 chiffres") String pin) {
    }

    public record Creation(
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Size(max = 100) String nom,
            @Size(max = 100) String prenom,
            @Size(max = 30) String telephone,
            @NotEmpty Set<Role> roles,
            @NotBlank @Size(min = LONGUEUR_MOT_DE_PASSE, max = 72) String motDePasse) {
    }

    /** Le mot de passe ne se modifie pas ici : il a son propre point d'entree. */
    public record Modification(
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Size(max = 100) String nom,
            @Size(max = 100) String prenom,
            @Size(max = 30) String telephone,
            @NotEmpty Set<Role> roles) {
    }

    /** Reinitialisation par un administrateur. */
    public record ReinitialisationMotDePasse(
            @NotBlank @Size(min = LONGUEUR_MOT_DE_PASSE, max = 72) String motDePasse) {
    }

    /** Changement par l'utilisateur lui-meme, qui doit prouver l'ancien. */
    public record ChangementMotDePasse(
            @NotBlank String motDePasseActuel,
            @NotBlank @Size(min = LONGUEUR_MOT_DE_PASSE, max = 72) String nouveauMotDePasse) {
    }

    public record ProfilRole(String valeur, String libelle) {
    }
}
