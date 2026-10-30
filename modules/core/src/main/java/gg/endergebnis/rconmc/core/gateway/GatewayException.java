package gg.endergebnis.rconmc.core.gateway;

/** Verbindungs-, Protokoll- oder Authentifizierungsfehler beim Zugriff auf einen Minecraft-Server. */
public class GatewayException extends RuntimeException {

    public GatewayException(String message) {
        super(message);
    }

    public GatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
