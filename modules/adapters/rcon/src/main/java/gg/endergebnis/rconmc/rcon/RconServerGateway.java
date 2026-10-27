package gg.endergebnis.rconmc.rcon;

import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import gg.endergebnis.rconmc.core.GatewayException;
import gg.endergebnis.rconmc.core.MinecraftServerGateway;
import gg.endergebnis.rconmc.core.ServerTarget;
import gg.endergebnis.rconmc.core.ServerType;

/**
 * RCON-Implementierung des {@link MinecraftServerGateway}: eine gecachte {@link RconClient}-Verbindung je Server-ID
 * (jeder Client serialisiert nur seine eigenen Aufrufe, Server blockieren sich nicht gegenseitig). Aendern sich
 * Host, Port oder Passwort eines Servers, wird der alte Client verworfen und neu aufgebaut.
 */
public final class RconServerGateway implements MinecraftServerGateway, AutoCloseable {

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final int RETRIES = 3;

    private record Key(String host, int port, String secret) {
        @Override
        public String toString() {
            return "Key[host=" + host + ", port=" + port + ", secret=[REDACTED]]";
        }
    }

    private record Entry(Key key, RconClient client) {}

    private final ConcurrentHashMap<Long, Entry> clients = new ConcurrentHashMap<>();

    @Override
    public ServerType type() {
        return ServerType.RCON;
    }

    @Override
    public void testConnection(ServerTarget target) {
        RconClient client = new RconClient(target.host(), target.port(), target.secret());
        try {
            client.execute("list", 0);
        } finally {
            client.close();
        }
    }

    @Override
    public void whitelistAdd(ServerTarget target, String mcName) {
        clientFor(target).execute("whitelist add " + requireValid(mcName), RETRIES);
    }

    @Override
    public void whitelistRemove(ServerTarget target, String mcName) {
        clientFor(target).execute("whitelist remove " + requireValid(mcName), RETRIES);
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

    private RconClient clientFor(ServerTarget target) {
        Key key = new Key(target.host(), target.port(), target.secret());
        return clients.compute(target.serverId(), (id, old) -> {
            if (old != null && old.key().equals(key)) {
                return old;
            }
            if (old != null) {
                old.client().close();
            }
            return new Entry(key, new RconClient(key.host(), key.port(), key.secret()));
        }).client();
    }

    /** Schutz vor Befehlsinjektion: nur gueltige Java-Namen gelangen in einen RCON-Befehl. */
    private static String requireValid(String mcName) {
        if (mcName == null || !VALID_NAME.matcher(mcName).matches()) {
            throw new GatewayException("Ungueltiger Minecraft-Name fuer RCON-Befehl");
        }
        return mcName;
    }
}
