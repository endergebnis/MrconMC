package gg.endergebnis.rconmc.core.gateway;

/** Wie der Bot mit einem Minecraft-Server spricht. Der Name wird in der Datenbank gespeichert, Werte nie umbenennen. */
public enum ServerType {
    /** Source-RCON (TCP, unverschluesselt). */
    RCON,
    /** Minecraft Server Management Protocol (ab 1.21.9) ueber {@code ws://}, also mit {@code management-server-tls-enabled=false}. */
    MGMT_WS,
    /** Minecraft Server Management Protocol ueber {@code wss://}, also mit aktiviertem TLS (Standard des Servers). */
    MGMT_WSS;

    public boolean isManagementProtocol() {
        return this == MGMT_WS || this == MGMT_WSS;
    }
}
