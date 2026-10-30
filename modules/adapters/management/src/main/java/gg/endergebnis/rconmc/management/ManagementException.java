package gg.endergebnis.rconmc.management;

import gg.endergebnis.rconmc.core.gateway.GatewayException;

/** Verbindungs-, Authentifizierungs- oder JSON-RPC-Fehler bei der Kommunikation per Management Protocol. */
public class ManagementException extends GatewayException {

    public ManagementException(String message) {
        super(message);
    }

    public ManagementException(String message, Throwable cause) {
        super(message, cause);
    }
}
