package tg.parcauto.api.plateforme.module;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controleur du point de vente ouvert a un compte qui n'a que le profil ASSOCIE.
 * Tous les autres lui sont fermes, quelles que soient leurs regles de profil :
 * un associe ne voit que sa part des benefices.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface OuvertAuxAssocies {
}
