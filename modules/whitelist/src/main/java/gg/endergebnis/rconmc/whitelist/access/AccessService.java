package gg.endergebnis.rconmc.whitelist.access;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import gg.endergebnis.rconmc.core.whitelist.BatchResult;
import gg.endergebnis.rconmc.core.whitelist.WhitelistStatus;
import gg.endergebnis.rconmc.persistence.player.Player;
import gg.endergebnis.rconmc.persistence.player.PlayerRepository;
import gg.endergebnis.rconmc.persistence.whitelist.WhitelistBypass;
import gg.endergebnis.rconmc.persistence.whitelist.WhitelistBypassRepository;
import gg.endergebnis.rconmc.whitelist.guild.GuildConfigService;
import gg.endergebnis.rconmc.whitelist.sync.WhitelistSynchronizer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Freigabeliste fuer die geschlossene Whitelist, durchgehend guild-bezogen. Wer freigegeben ist, darf sich auch bei
 * {@code CLOSED} registrieren und wird beim Schliessen nicht von den Servern entfernt. Die Berechtigungspruefung
 * (wer darf freigeben?) liegt in der Discord-Schicht, hier gibt es nur die Fachlogik.
 */
@Service
public class AccessService {

    private final WhitelistBypassRepository bypasses;
    private final PlayerRepository players;
    private final GuildConfigService guilds;
    private final WhitelistSynchronizer synchronizer;

    public AccessService(
            WhitelistBypassRepository bypasses,
            PlayerRepository players,
            GuildConfigService guilds,
            WhitelistSynchronizer synchronizer) {
        this.bypasses = bypasses;
        this.players = players;
        this.guilds = guilds;
        this.synchronizer = synchronizer;
    }

    public boolean isAllowed(long guildId, String discordId) {
        return bypasses.existsByGuildIdAndDiscordId(guildId, discordId);
    }

    /** Gibt einen Discord-Nutzer frei. Ist er registriert und die Whitelist geschlossen, kommt er sofort auf die Server. */
    public AllowResult allow(long guildId, String discordId, String grantedBy) {
        if (bypasses.existsByGuildIdAndDiscordId(guildId, discordId)) {
            return new AllowResult.AlreadyAllowed();
        }
        guilds.getOrCreate(guildId); // Fremdschluessel: guild_config muss existieren
        try {
            bypasses.save(new WhitelistBypass(guildId, discordId, grantedBy, Instant.now()));
        } catch (DataIntegrityViolationException e) {
            return new AllowResult.AlreadyAllowed(); // parallel bereits freigegeben
        }
        Optional<Player> player = players.findByGuildIdAndDiscordId(guildId, discordId);
        if (player.isEmpty()) {
            return new AllowResult.AllowedUnregistered();
        }
        Player registered = player.get();
        if (guilds.status(guildId) == WhitelistStatus.OPEN) {
            return new AllowResult.AllowedWhileOpen(registered.mcUsername());
        }
        BatchResult result = synchronizer.run(guildId, false, server -> List.of(registered));
        return new AllowResult.AllowedAndAdded(registered.mcUsername(), result);
    }

    /** Nimmt die Freigabe zurueck. Bei geschlossener Whitelist wird ein registrierter Spieler von den Servern entfernt. */
    public RevokeResult revoke(long guildId, String discordId) {
        if (bypasses.deleteByGuildAndDiscord(guildId, discordId) == 0) {
            return new RevokeResult.NotAllowed();
        }
        Optional<Player> player = players.findByGuildIdAndDiscordId(guildId, discordId);
        if (player.isEmpty() || guilds.status(guildId) == WhitelistStatus.OPEN) {
            return new RevokeResult.Revoked();
        }
        Player registered = player.get();
        BatchResult result = synchronizer.run(guildId, true, server -> List.of(registered));
        return new RevokeResult.RevokedAndRemoved(registered.mcUsername(), result);
    }

    public long count(long guildId) {
        return bypasses.countByGuildId(guildId);
    }

    public List<AllowedUser> list(long guildId) {
        return bypasses.findByGuildIdOrderByIdAsc(guildId).stream()
                .map(b -> new AllowedUser(
                        b.discordId(),
                        players.findByGuildIdAndDiscordId(guildId, b.discordId())
                                .map(Player::mcUsername)
                                .orElse(null),
                        b.grantedBy(),
                        b.grantedAt()))
                .toList();
    }
}
