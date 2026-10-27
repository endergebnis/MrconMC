package gg.endergebnis.rconmc.rcon;

import gg.endergebnis.rconmc.core.GatewayException;

/** Verbindungs-, Protokoll- oder Authentifizierungsfehler bei der RCON-Kommunikation. */
public class RconException extends GatewayException {

    public RconException(String message) {
        super(message);
    }

    public RconException(String message, Throwable cause) {
        super(message, cause);
    }
}
