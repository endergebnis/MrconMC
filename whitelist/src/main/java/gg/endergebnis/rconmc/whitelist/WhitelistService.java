package gg.endergebnis.rconmc.whitelist;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import gg.endergebnis.rconmc.core.AccountVerifier;
import gg.endergebnis.rconmc.core.BatchResult;
import gg.endergebnis.rconmc.core.GatewayException;
import gg.endergebnis.rconmc.core.MinecraftServerGateway;
import gg.endergebnis.rconmc.core.RegistrationResult;
import gg.endergebnis.rconmc.core.VerificationException;
import gg.endergebnis.rconmc.core.VerifiedAccount;
import gg.endergebnis.rconmc.core.WhitelistStatus;
import gg.endergebnis.rconmc.persistence.Player;
import gg.endergebnis.rconmc.persistence.PlayerRepository;
import gg.endergebnis.rconmc.persistence.WhitelistEntry;
import gg.endergebnis.rconmc.persistence.WhitelistEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Fachlogik der Registrierung und der Admin-Sammelaktionen, durchgehend guild-bezogen. Ein Spieler wird auf allen
 * aktiven Servern seiner Guild eingetragen. Kein Datenbankzugriff laeuft in einer Transaktion, die einen
 * Server-Aufruf umschliesst (SQLite hat nur einen Schreiber).
 */
@Service
public class WhitelistService {

    private static final Logger log = LoggerFactory.getLogger(WhitelistService.class);

    private final PlayerRepository players;
    private final WhitelistEntryRepository entries;
    private final GuildConfigService guilds;
    private final ServerService servers;
    private final GatewayRouter router;
    private final AccountVerifier verifier;

    public WhitelistService(
            PlayerRepository players,
            WhitelistEntryRepository entries,
            GuildConfigService guilds,
            ServerService servers,
            GatewayRouter router,
            AccountVerifier verifier) {
        this.players = players;
        this.entries = entries;
        this.guilds = guilds;
        this.servers = servers;
        this.router = router;
        this.verifier = verifier;
    }

    // ---- Registrierung -------------------------------------------------------------------------------

    public RegistrationResult register(long guildId, String discordId, String discordUsername, String mcName) {
        Optional<VerifiedAccount> verified;
        try {
            verified = verifier.verify(mcName);
        } catch (VerificationException e) {
            log.error("Verifizierung fehlgeschlagen: {}", e.getMessage(), e);
            return new RegistrationResult.VerificationFailed();
        }
        if (verified.isEmpty()) {
            return new RegistrationResult.NotFound();
        }
        VerifiedAccount account = verified.get();

        if (players.existsByGuildIdAndMcUuidAndDiscordIdNot(guildId, account.uuid(), discordId)) {
            return new RegistrationResult.Conflict();
        }

        Instant now = Instant.now();
        Player player = players.findByGuildIdAndDiscordId(guildId, discordId)
                .orElseGet(() -> new Player(guildId, discordId, now));
        // Wechselt der Account, passen die bisherigen Server-Eintraege nicht mehr zum neuen Namen.
        boolean accountChanged = player.id() != null && !account.uuid().equals(player.mcUuid());
        player.register(discordUsername, account.name(), account.uuid(), now);
        player = players.save(player);
        if (accountChanged) {
            entries.deleteByPlayer(player.id());
        }

        if (guilds.status(guildId) != WhitelistStatus.OPEN) {
            return new RegistrationResult.Pending(account.name());
        }
        List<ActiveServer> active = servers.activeServers(guildId);
        if (active.isEmpty()) {
            return new RegistrationResult.NoServers(account.name());
        }

        List<String> failed = new ArrayList<>();
        for (ActiveServer server : active) {
            try {
                addAndRecord(player, server);
            } catch (GatewayException e) {
                log.error("Whitelist-Eintrag fuer {} auf Server {} fehlgeschlagen: {}",
                        account.name(), server.id(), e.getMessage());
                failed.add(server.name());
            }
        }
        if (failed.isEmpty()) {
            return new RegistrationResult.Whitelisted(account.name(), active.size());
        }
        if (failed.size() == active.size()) {
            return new RegistrationResult.WhitelistFailed(account.name());
        }
        return new RegistrationResult.PartiallyWhitelisted(account.name(), failed);
    }

    // ---- Admin-Aktionen ------------------------------------------------------------------------------

    /** Oeffnet die Whitelist der Guild und traegt alle Spieler nach, die auf einem Server noch fehlen. */
    public BatchResult open(long guildId) {
        guilds.setStatus(guildId, WhitelistStatus.OPEN);
        return batch(guildId, false, true);
    }

    /** Schliesst die Whitelist der Guild: neue Registrierungen werden nur noch vorgemerkt. */
    public void close(long guildId) {
        guilds.setStatus(guildId, WhitelistStatus.CLOSED);
    }

    /** Setzt alle Spieler der Guild auf alle aktiven Server. */
    public BatchResult openAll(long guildId) {
        return batch(guildId, false, false);
    }

    /** Entfernt alle Spieler der Guild von allen aktiven Servern. */
    public BatchResult closeAll(long guildId) {
        return batch(guildId, true, false);
    }

    /**
     * Faellt ein Server aus (Verbindung/Auth), wird er fuer den Rest der Aktion uebersprungen, statt fuer jeden
     * Spieler erneut Timeouts abzuwarten; die uebersprungenen Paare zaehlen als nicht erfolgreich.
     */
    private BatchResult batch(long guildId, boolean remove, boolean onlyMissing) {
        int succeeded = 0;
        int total = 0;
        for (ActiveServer server : servers.activeServers(guildId)) {
            List<Player> targets = onlyMissing
                    ? players.findMissingOnServer(guildId, server.id())
                    : players.findByGuildIdOrderByIdAsc(guildId);
            total += targets.size();
            MinecraftServerGateway gateway = router.get(server.target().type());
            for (Player player : targets) {
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

    // ---- Abfragen ------------------------------------------------------------------------------------

    public Optional<PlayerInfo> findByMcUsername(long guildId, String mcUsername) {
        return players.findFirstByGuildIdAndMcUsernameIgnoreCase(guildId, mcUsername).map(this::info);
    }

    public Optional<PlayerInfo> findByDiscordUsername(long guildId, String discordUsername) {
        return players.findFirstByGuildIdAndDiscordUsernameIgnoreCase(guildId, discordUsername).map(this::info);
    }

    public WhitelistStatus whitelistStatus(long guildId) {
        return guilds.status(guildId);
    }

    private PlayerInfo info(Player p) {
        return new PlayerInfo(
                p.discordId(), p.discordUsername(), p.mcUsername(), p.platform(), entries.serverNamesOfPlayer(p.id()));
    }
}
