package gg.endergebnis.rconmc.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Verknuepfung zwischen einer Discord- und einer Minecraft-Identitaet <em>innerhalb einer Guild</em>. Pro Guild gibt
 * es je Discord-Nutzer und je Minecraft-UUID hoechstens einen Eintrag.
 */
@Entity
@Table(name = "player")
public class Player {

    public static final String PLATFORM_JAVA = "java";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long guildId;

    @Column(nullable = false)
    private String discordId;

    @Column(nullable = false)
    private String discordUsername;

    @Column(nullable = false)
    private String mcUsername;

    @Column(nullable = false)
    private String mcUuid;

    @Column(nullable = false)
    private String platform = PLATFORM_JAVA;

    @Column(nullable = false)
    private Instant registeredAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Player() {}

    public Player(long guildId, String discordId, Instant now) {
        this.guildId = guildId;
        this.discordId = discordId;
        this.registeredAt = now;
        this.updatedAt = now;
    }

    public void register(String discordUsername, String mcUsername, String mcUuid, Instant now) {
        this.discordUsername = discordUsername;
        this.mcUsername = mcUsername;
        this.mcUuid = mcUuid;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public long guildId() {
        return guildId;
    }

    public String discordId() {
        return discordId;
    }

    public String discordUsername() {
        return discordUsername;
    }

    public String mcUsername() {
        return mcUsername;
    }

    public String mcUuid() {
        return mcUuid;
    }

    public String platform() {
        return platform;
    }
}
