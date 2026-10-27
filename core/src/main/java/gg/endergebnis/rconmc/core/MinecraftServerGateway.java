package gg.endergebnis.rconmc.core;

/**
 * Port zu einem Minecraft-Server. Implementierungen (RCON, spaeter Management Protocol) werden anhand von
 * {@link #type()} ausgewaehlt; die Fachlogik kennt nur dieses Interface.
 */
public interface MinecraftServerGateway {

    ServerType type();

    /** Baut eine Wegwerf-Verbindung auf (inkl. Authentifizierung), ohne etwas zu cachen. */
    void testConnection(ServerTarget target) throws GatewayException;

    void whitelistAdd(ServerTarget target, String mcName) throws GatewayException;

    void whitelistRemove(ServerTarget target, String mcName) throws GatewayException;

    /** Verwirft eine gecachte Verbindung (Server geloescht oder Konfiguration geaendert). */
    void evict(long serverId);
}
