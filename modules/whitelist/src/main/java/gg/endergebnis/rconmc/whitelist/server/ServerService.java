package gg.endergebnis.rconmc.whitelist.server;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import gg.endergebnis.rconmc.core.GatewayException;
import gg.endergebnis.rconmc.core.ServerTarget;
import gg.endergebnis.rconmc.core.ServerType;
import gg.endergebnis.rconmc.crypto.SecretCipher;
import gg.endergebnis.rconmc.persistence.server.MinecraftServer;
import gg.endergebnis.rconmc.persistence.server.MinecraftServerRepository;
import gg.endergebnis.rconmc.persistence.whitelist.WhitelistEntryRepository;
import gg.endergebnis.rconmc.whitelist.guild.GuildConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verwaltung der Minecraft-Server einer Guild. Jede Methode bekommt die {@code guildId} der aufrufenden Guild und
 * filtert damit; Server anderer Guilds sind nie erreichbar. Passwoerter werden hier verschluesselt (AES-256-GCM, die
 * Guild-ID ist als AAD gebunden) und nur fuer die interne Fachlogik wieder entschluesselt - Anzeige-Typen enthalten
 * kein Secret.
 */
@Service
public class ServerService {

    private static final Logger log = LoggerFactory.getLogger(ServerService.class);

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final Pattern VALID_HOST = Pattern.compile("[A-Za-z0-9._:-]{1,253}");
    private static final int MAX_SECRET_LENGTH = 256;

    private final MinecraftServerRepository servers;
    private final WhitelistEntryRepository entries;
    private final GuildConfigService guilds;
    private final SecretCipher cipher;
    private final GatewayRouter router;
    private final ServerLimits limits;
    private final HostPolicy hostPolicy;

    public ServerService(
            MinecraftServerRepository servers,
            WhitelistEntryRepository entries,
            GuildConfigService guilds,
            SecretCipher cipher,
            GatewayRouter router,
            ServerLimits limits,
            HostPolicy hostPolicy) {
        this.servers = servers;
        this.entries = entries;
        this.guilds = guilds;
        this.cipher = cipher;
        this.router = router;
        this.limits = limits;
        this.hostPolicy = hostPolicy;
    }

    public AddServerResult add(long guildId, String name, ServerType type, String host, int port, String secret) {
        if (name == null || !VALID_NAME.matcher(name).matches()) {
            return new AddServerResult.Invalid("Der Name darf nur aus Buchstaben, Ziffern, _ und - bestehen (max. 32).");
        }
        if (host == null || !VALID_HOST.matcher(host).matches()) {
            return new AddServerResult.Invalid("Ungültiger Host.");
        }
        if (port < 1 || port > 65535) {
            return new AddServerResult.Invalid("Der Port muss zwischen 1 und 65535 liegen.");
        }
        if (secret == null || secret.isBlank() || secret.length() > MAX_SECRET_LENGTH) {
            return new AddServerResult.Invalid("Das Passwort fehlt oder ist zu lang.");
        }
        Optional<String> denied = hostPolicy.check(host);
        if (denied.isPresent()) {
            return new AddServerResult.HostNotAllowed(denied.get());
        }
        if (servers.countByGuildId(guildId) >= limits.maxPerGuild()) {
            return new AddServerResult.LimitReached(limits.maxPerGuild());
        }
        if (servers.findByGuildIdAndNameIgnoreCase(guildId, name).isPresent()) {
            return new AddServerResult.Duplicate(name);
        }
        try {
            router.get(type).testConnection(new ServerTarget(0L, type, host, port, secret));
        } catch (GatewayException e) {
            return new AddServerResult.ConnectionFailed(e.getMessage());
        }
        guilds.getOrCreate(guildId);
        try {
            servers.save(new MinecraftServer(
                    guildId, name, type, host, port, cipher.encrypt(secret, context(guildId)), Instant.now()));
        } catch (DataIntegrityViolationException e) {
            return new AddServerResult.Duplicate(name);
        }
        return new AddServerResult.Added(name);
    }

    public List<ServerSummary> list(long guildId) {
        return servers.findByGuildIdOrderByIdAsc(guildId).stream()
                .map(s -> new ServerSummary(s.name(), s.type(), s.host(), s.port(), s.enabled()))
                .toList();
    }

    /** @return {@code true}, wenn der Server existierte und entfernt wurde */
    @Transactional
    public boolean remove(long guildId, String name) {
        Optional<MinecraftServer> server = servers.findByGuildIdAndNameIgnoreCase(guildId, name);
        if (server.isEmpty()) {
            return false;
        }
        long id = server.get().id();
        entries.deleteByServer(id);
        servers.delete(server.get());
        router.evict(id);
        return true;
    }

    public TestResult test(long guildId, String name) {
        Optional<MinecraftServer> server = servers.findByGuildIdAndNameIgnoreCase(guildId, name);
        if (server.isEmpty()) {
            return new TestResult.NotFound();
        }
        try {
            ServerTarget target = target(server.get());
            router.get(target.type()).testConnection(target);
            return new TestResult.Ok();
        } catch (GatewayException e) {
            return new TestResult.Failed(e.getMessage());
        } catch (IllegalStateException e) {
            log.error("Secret von Server {} nicht entschluesselbar", server.get().id(), e);
            return new TestResult.Failed("Das gespeicherte Passwort ist nicht lesbar - bitte Server neu hinzufügen.");
        }
    }

    /** Aktive Server der Guild mit entschluesselten Daten; nicht entschluesselbare Server werden geloggt und uebersprungen. */
    public List<ActiveServer> activeServers(long guildId) {
        List<ActiveServer> result = new ArrayList<>();
        for (MinecraftServer server : servers.findByGuildIdAndEnabledTrueOrderByIdAsc(guildId)) {
            try {
                result.add(new ActiveServer(server.id(), server.name(), target(server)));
            } catch (IllegalStateException e) {
                log.error("Secret von Server {} (Guild {}) nicht entschluesselbar - uebersprungen", server.id(), guildId, e);
            }
        }
        return result;
    }

    private ServerTarget target(MinecraftServer server) {
        return new ServerTarget(
                server.id(),
                server.type(),
                server.host(),
                server.port(),
                cipher.decrypt(server.secretEnc(), context(server.guildId())));
    }

    private static String context(long guildId) {
        return "minecraft_server.secret:" + guildId;
    }
}
