package gg.endergebnis.rconmc.management;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;

import gg.endergebnis.rconmc.core.gateway.GatewayException;
import gg.endergebnis.rconmc.core.gateway.MinecraftServerGateway;
import gg.endergebnis.rconmc.core.gateway.ServerTarget;
import gg.endergebnis.rconmc.core.gateway.ServerType;

/**
 * {@link MinecraftServerGateway} fuer das Minecraft Server Management Protocol. Eine Instanz bedient genau einen
 * {@link ServerType} ({@link ServerType#MGMT_WS} ohne, {@link ServerType#MGMT_WSS} mit TLS); der Router waehlt sie
 * anhand des Typs. Wie beim RCON-Gateway gibt es eine gecachte Verbindung je Server-ID, die bei geaendertem Host,
 * Port oder Secret neu aufgebaut wird.
 *
 * <p>Das Protokoll nennt die Whitelist "allowlist": {@code minecraft:allowlist} (lesen),
 * {@code minecraft:allowlist/add} und {@code minecraft:allowlist/remove}, jeweils mit einer Liste von Spielerobjekten
 * ({@code {"name": ...}}). Beim Eintragen wird nur der Name uebergeben, damit der Server die UUID selbst aufloest
 * (Online- und Offline-Modus verhalten sich dann wie bei {@code whitelist add} per RCON).
 */
public final class ManagementServerGateway implements MinecraftServerGateway, AutoCloseable {

    static final String METHOD_LIST = "minecraft:allowlist";
    static final String METHOD_ADD = "minecraft:allowlist/add";
    static final String METHOD_REMOVE = "minecraft:allowlist/remove";

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final int RETRIES = 3;

    private record Key(String host, int port, String secret) {
        @Override
        public String toString() {
            return "Key[host=" + host + ", port=" + port + ", secret=[REDACTED]]";
        }
    }

    private record Entry(Key key, ManagementClient client) {}

    private final ServerType type;
    private final SSLContext sslContext;
    private final ConcurrentHashMap<Long, Entry> clients = new ConcurrentHashMap<>();

    /** @param sslContext Vertrauensbasis fuer {@code wss://}; {@code null} = Standard-Truststore der JVM */
    public ManagementServerGateway(ServerType type, SSLContext sslContext) {
        if (!type.isManagementProtocol()) {
            throw new IllegalArgumentException("Kein Management-Protocol-Typ: " + type);
        }
        this.type = type;
        this.sslContext = sslContext;
    }

    @Override
    public ServerType type() {
        return type;
    }

    @Override
    public void testConnection(ServerTarget target) {
        ManagementClient client = newClient(target.host(), target.port(), target.secret());
        try {
            // Liest die Allowlist: prueft Verbindung, Authentifizierung und dass die Methode existiert.
            players(client.call(METHOD_LIST, List.of(), 0));
        } finally {
            client.close();
        }
    }

    @Override
    public void whitelistAdd(ServerTarget target, String mcName) {
        String name = requireValid(mcName);
        clientFor(target).call(METHOD_ADD, List.of(List.of(Map.of("name", name))), RETRIES);
    }

    @Override
    public void whitelistRemove(ServerTarget target, String mcName) {
        String name = requireValid(mcName);
        ManagementClient client = clientFor(target);
        // Nach dem Eintrag auf dem Server suchen und mit dessen id entfernen: Das funktioniert unabhaengig davon,
        // ob der Server den Namen selbst aufloesen kann. Steht der Name nicht (mehr) drin, ist nichts zu tun.
        for (Map<String, Object> player : players(client.call(METHOD_LIST, List.of(), RETRIES))) {
            if (name.equalsIgnoreCase(String.valueOf(player.get("name")))) {
                client.call(METHOD_REMOVE, List.of(List.of(player)), RETRIES);
                return;
            }
        }
    }

    @Override
    public void evict(long serverId) {
        Entry removed = clients.remove(serverId);
        if (removed != null) {
            removed.client().close();
        }
    }

    @Override
    public void close() {
        clients.keySet().forEach(this::evict);
    }

    private ManagementClient newClient(String host, int port, String secret) {
        return new ManagementClient(host, port, secret, type == ServerType.MGMT_WSS, sslContext);
    }

    private ManagementClient clientFor(ServerTarget target) {
        Key key = new Key(target.host(), target.port(), target.secret());
        return clients.compute(target.serverId(), (id, old) -> {
            if (old != null && old.key().equals(key)) {
                return old;
            }
            if (old != null) {
                old.client().close();
            }
            return new Entry(key, newClient(key.host(), key.port(), key.secret()));
        }).client();
    }

    /** Liest die Spielerliste aus einer Antwort; akzeptiert eine nackte Liste ebenso wie {@code {"allowlist":[...]}}. */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> players(Object result) {
        Object list = result;
        if (result instanceof Map<?, ?> map) {
            list = map.get("allowlist");
        }
        if (!(list instanceof List<?> entries)) {
            throw new ManagementException("Unerwartete Antwort des Servers auf " + METHOD_LIST);
        }
        List<Map<String, Object>> players = new ArrayList<>();
        for (Object entry : entries) {
            if (entry instanceof Map<?, ?> player) {
                players.add(new LinkedHashMap<>((Map<String, Object>) player));
            }
        }
        return players;
    }

    private static String requireValid(String mcName) {
        if (mcName == null || !VALID_NAME.matcher(mcName).matches()) {
            throw new GatewayException("Ungueltiger Minecraft-Name");
        }
        return mcName;
    }
}
