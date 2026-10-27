package gg.endergebnis.rconmc.core;

/** Entschluesselte Verbindungsdaten eines Servers. Das Secret taucht in keiner {@code toString()}-Ausgabe auf. */
public record ServerTarget(long serverId, ServerType type, String host, int port, String secret) {

    @Override
    public String toString() {
        return "ServerTarget[serverId=" + serverId + ", type=" + type + ", host=" + host + ", port=" + port
                + ", secret=[REDACTED]]";
    }
}
