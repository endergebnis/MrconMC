package gg.endergebnis.rconmc.whitelist;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import gg.endergebnis.rconmc.core.verification.AccountVerifier;
import gg.endergebnis.rconmc.core.verification.VerificationException;
import gg.endergebnis.rconmc.core.verification.VerifiedAccount;
import gg.endergebnis.rconmc.core.whitelist.BatchResult;
import gg.endergebnis.rconmc.core.whitelist.RegistrationResult;
import gg.endergebnis.rconmc.core.whitelist.WhitelistStatus;
import gg.endergebnis.rconmc.persistence.player.Player;
import gg.endergebnis.rconmc.persistence.player.PlayerRepository;
import gg.endergebnis.rconmc.persistence.whitelist.WhitelistEntryRepository;
import gg.endergebnis.rconmc.whitelist.access.AccessService;
import gg.endergebnis.rconmc.whitelist.guild.GuildConfigService;
import gg.endergebnis.rconmc.whitelist.sync.SyncResult;
import gg.endergebnis.rconmc.whitelist.sync.WhitelistSynchronizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Fachlogik der Registrierung und der Admin-Sammelaktionen, durchgehend guild-bezogen. Ein Spieler wird auf allen
 * aktiven Servern seiner Guild eingetragen.
 *
 * <p>Whitelist-Status: Bei {@code OPEN} wird jede Registrierung sofort eingetragen. Bei {@code CLOSED} werden nur
 * Nutzer mit Freigabe ({@link AccessService}) eingetragen, alle anderen als "pending" vorgemerkt; beim Schliessen
 * werden registrierte Spieler ohne Freigabe von den Servern entfernt. Die Server-Zugriffe laufen ueber den
 * {@link WhitelistSynchronizer}.
 */
@Service
public class WhitelistService {

    private static final Logger log = LoggerFactory.getLogger(WhitelistService.class);

    private final PlayerRepository players;
    private final WhitelistEntryRepository entries;
    private final GuildConfigService guilds;
    private final AccessService access;
    private final WhitelistSynchronizer synchronizer;
    private final AccountVerifier verifier;

    public WhitelistService(
            PlayerRepository players,
            WhitelistEntryRepository entries,
            GuildConfigService guilds,
            AccessService access,
            WhitelistSynchronizer synchronizer,
            AccountVerifier verifier) {
        this.players = players;
        this.entries = entries;
        this.guilds = guilds;
        this.access = access;
        this.synchronizer = synchronizer;
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
        // Wechselt der Account, muss der alte Name von den Servern runter und die Eintraege passen nicht mehr.
        boolean accountChanged = player.id() != null && !account.uuid().equals(player.mcUuid());
        String previousName = player.mcUsername();
        player.register(discordUsername, account.name(), account.uuid(), now);
        player = players.save(player);
        if (accountChanged) {
            synchronizer.evictPreviousAccount(guildId, player.id(), previousName);
        }

        boolean admitted = guilds.status(guildId) == WhitelistStatus.OPEN || access.isAllowed(guildId, discordId);
        if (!admitted) {
            return new RegistrationResult.Pending(account.name());
        }

        SyncResult sync = synchronizer.addToAllServers(guildId, player);
        if (sync.servers() == 0) {
            return new RegistrationResult.NoServers(account.name());
        }
        if (sync.failedServers().isEmpty()) {
            return new RegistrationResult.Whitelisted(account.name(), sync.servers());
        }
        if (sync.failedServers().size() == sync.servers()) {
            return new RegistrationResult.WhitelistFailed(account.name());
        }
        return new RegistrationResult.PartiallyWhitelisted(account.name(), sync.failedServers());
    }

    // ---- Admin-Aktionen ------------------------------------------------------------------------------

    /** Oeffnet die Whitelist der Guild und traegt alle Spieler nach, die auf einem Server noch fehlen. */
    public BatchResult open(long guildId) {
        guilds.setStatus(guildId, WhitelistStatus.OPEN);
        return synchronizer.run(guildId, false, server -> players.findMissingOnServer(guildId, server.id()));
    }

    /**
     * Schliesst die Whitelist der Guild: neue Registrierungen ohne Freigabe werden nur noch vorgemerkt.
     *
     * @param removeOthers {@code true}: registrierte Spieler ohne Freigabe werden zusaetzlich von allen Servern
     *     entfernt, damit wirklich nur Freigegebene joinen koennen (bleiben registriert, {@link #open} traegt sie
     *     wieder ein). {@code false}: bereits eingetragene Spieler bleiben stehen, es werden nur neue gestoppt.
     * @return das Ergebnis des Entfernens ({@code 0/0}, wenn nichts entfernt werden sollte)
     */
    public BatchResult close(long guildId, boolean removeOthers) {
        guilds.setStatus(guildId, WhitelistStatus.CLOSED);
        if (!removeOthers) {
            return new BatchResult(0, 0);
        }
        List<Player> others = players.findWithoutBypass(guildId);
        return synchronizer.run(guildId, true, server -> others);
    }

    /** Setzt alle Spieler der Guild auf alle aktiven Server. */
    public BatchResult openAll(long guildId) {
        return synchronizer.run(guildId, false, server -> players.findByGuildIdOrderByIdAsc(guildId));
    }

    /** Entfernt alle Spieler der Guild von allen aktiven Servern. */
    public BatchResult closeAll(long guildId) {
        return synchronizer.run(guildId, true, server -> players.findByGuildIdOrderByIdAsc(guildId));
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
                p.discordId(),
                p.discordUsername(),
                p.mcUsername(),
                p.platform(),
                entries.serverNamesOfPlayer(p.id()),
                access.isAllowed(p.guildId(), p.discordId()));
    }
}
