package gg.endergebnis.rconmc.core;

/** Die Verifizierung konnte nicht durchgefuehrt werden (Netzwerk, HTTP-Status, ...) - nicht: Name existiert nicht. */
public class VerificationException extends RuntimeException {

    public VerificationException(String message) {
        super(message);
    }

    public VerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
