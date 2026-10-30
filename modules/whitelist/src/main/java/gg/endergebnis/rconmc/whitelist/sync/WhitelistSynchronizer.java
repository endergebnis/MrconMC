package gg.endergebnis.rconmc.whitelist.sync;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import gg.endergebnis.rconmc.core.gateway.GatewayException;
import gg.endergebnis.rconmc.core.gateway.MinecraftServerGateway;
import gg.endergebnis.rconmc.core.whitelist.BatchResult;
import gg.endergebnis.rconmc.persistence.player.Player;
import gg.endergebnis.rconmc.persistence.whitelist.WhitelistEntry;
import gg.endergebnis.rconmc.persistence.whitelist.WhitelistEntryRepository;
import gg.endergebnis.rconmc.whitelist.server.ActiveServer;
import gg.endergebnis.rconmc.whitelist.server.GatewayRouter;
import gg.endergebnis.rconmc.whitelist.server.ServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Gleicht Spieler mit den Whitelists der Minecraft-Server einer Guild ab und fuehrt dabei {@code whitelist_entry}
 * mit. Alle Aenderungen an Server-Whitelists laufen ausschliesslich hier durch. Kein Datenbankzugriff laeuft in
 * einer Transaktion, die einen Server-Aufruf umschliesst (SQLite hat nur einen Schreiber).
 */
@Component
public class WhitelistSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(WhitelistSynchronizer.class);

    private final WhitelistEntryRepository entries;
    private final ServerService servers;
    private final GatewayRouter router;

    public WhitelistSynchronizer(WhitelistEntryRepository entries, ServerService servers, GatewayRouter router) {
        this.entries = entries;
        this.servers = servers;
        this.router = router;
    }

    /** Traegt einen Spieler auf allen aktiven Servern der Guild ein; ein Fehler auf einem Server stoppt die anderen nicht. */
    public SyncResult addToAllServers(long guildId, Player player) {
        List<ActiveServer> active = servers.activeServers(guildId);
        List<String> failed = new ArrayList<>();
        for (ActiveServer server : active) {
            try {
                addAndRecord(player, server);
            } catch (GatewayException e) {
                log.error("Whitelist-Eintrag fuer {} auf Server {} fehlgeschlagen: {}",
                        player.mcUsername(), server.id(), e.getMessage());
                failed.add(server.name());
            }
        }
        return new SyncResult(active.size(), failed);
    }

    /**
     * Entfernt einen frueheren Minecraft-Namen von allen aktiven Servern (best effort) und vergisst alle Eintraege
     * des Spielers, damit der neue Name spaeter sauber nachgetragen wird. Ein nicht erreichbarer Server wird nur
     * geloggt: Der alte Name bleibt dort bis zu einer manuellen Bereinigung stehen.
     */
    public void evictPreviousAccount(long guildId, long playerId, String previousMcName) {
        if (previousMcName != null) {
            for (ActiveServer server : servers.activeServers(guildId)) {
                try {
                    router.get(server.target().type()).whitelistRemove(server.target(), previousMcName);
                } catch (GatewayException e) {
                    log.warn("Alter Name {} konnte auf Server {} nicht entfernt werden: {}",
                            previousMcName, server.id(), e.getMessage());
                }
            }
        }
        entries.deleteByPlayer(playerId);
    }

    /**
     * Sammelaktion ueber alle aktiven Server der Guild: {@code targets} liefert je Server die zu bearbeitenden
     * Spieler. Faellt ein Server aus (Verbindung/Auth), wird er fuer den Rest der Aktion uebersprungen, statt fuer
     * jeden Spieler erneut Timeouts abzuwarten; die uebersprungenen Paare zaehlen als nicht erfolgreich.
     */
    public BatchResult run(long guildId, boolean remove, Function<ActiveServer, List<Player>> targets) {
        int succeeded = 0;
        int total = 0;
        for (ActiveServer server : servers.activeServers(guildId)) {
            List<Player> players = targets.apply(server);
            total += players.size();
            MinecraftServerGateway gateway = router.get(server.target().type());
            for (Player player : players) {
                try {
                    if (remove) {
                        gateway.whitelistRemove(server.target(), player.mcUsername());
                        entries.deleteByPlayerAndServer(player.id(), server.id());
                    } else {
                        addAndRecord(player, server);
                    }
                    succeeded++;
                } catch (GatewayException e) {
                    log.error("Server {} nicht erreichbar, ueberspringe den Rest: {}", server.id(), e.getMessage());
                    break;
                } catch (RuntimeException e) {
                    log.error("DB-Update fehlgeschlagen fuer {}: {}", player.mcUsername(), e.getMessage(), e);
                }
            }
        }
        return new BatchResult(succeeded, total);
    }

    private void addAndRecord(Player player, ActiveServer server) {
        router.get(server.target().type()).whitelistAdd(server.target(), player.mcUsername());
        if (!entries.existsByPlayerIdAndServerId(player.id(), server.id())) {
            entries.save(new WhitelistEntry(player.id(), server.id(), Instant.now()));
        }
    }
}
