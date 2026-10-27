package gg.endergebnis.rconmc.persistence;

import java.time.Instant;

import gg.endergebnis.rconmc.core.ServerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Ein Minecraft-Server, der zu genau einer Guild gehoert. {@code secretEnc} ist das mit AES-256-GCM verschluesselte
 * RCON-Passwort; Ver- und Entschluesselung passieren ausschliesslich im Service, nie in dieser Klasse.
 */
@Entity
@Table(name = "minecraft_server")
public class MinecraftServer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long guildId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServerType type;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private int port;

    @Column(nullable = false)
    private String secretEnc;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private Instant createdAt;

    protected MinecraftServer() {}

    public MinecraftServer(
            long guildId, String name, ServerType type, String host, int port, String secretEnc, Instant now) {
        this.guildId = guildId;
        this.name = name;
        this.type = type;
        this.host = host;
        this.port = port;
        this.secretEnc = secretEnc;
        this.createdAt = now;
    }

    public Long id() {
        return id;
    }

    public long guildId() {
        return guildId;
    }

    public String name() {
        return name;
    }

    public ServerType type() {
        return type;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String secretEnc() {
        return secretEnc;
    }

    public boolean enabled() {
        return enabled;
    }

    @Override
    public String toString() {
        return "MinecraftServer[id=" + id + ", guildId=" + guildId + ", name=" + name + ", host=" + host
                + ", port=" + port + ", secretEnc=[REDACTED]]";
    }
}
