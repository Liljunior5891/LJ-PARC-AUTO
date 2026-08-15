package tg.parcauto.api.common;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String entite, Object id) {
        return new ResourceNotFoundException(entite + " introuvable (id=" + id + ")");
    }
}
