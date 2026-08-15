package tg.parcauto.api.common;

/** Regle metier violee : renvoyee au client en HTTP 409. */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
