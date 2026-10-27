package gg.endergebnis.rconmc.persistence.whitelist;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Freigabe fuer die geschlossene Whitelist: Ein Discord-Nutzer mit Eintrag darf sich auch dann registrieren und
 * bleibt auf den Servern, wenn die Whitelist der Guild {@code CLOSED} ist. Gebunden an die Discord-ID (nicht an den
 * Minecraft-Namen), damit die Freigabe schon vor der Registrierung vergeben werden kann und einen Namenswechsel
 * uebersteht.
 */
@Entity
@Table(name = "whitelist_bypass")
public class WhitelistBypass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long guildId;

    @Column(nullable = false)
    private String discordId;

    /** Discord-ID des Admins, der die Freigabe erteilt hat (Nachvollziehbarkeit). */
    @Column(nullable = false)
    private String grantedBy;

    @Column(nullable = false)
    private Instant grantedAt;

    protected WhitelistBypass() {}

    public WhitelistBypass(long guildId, String discordId, String grantedBy, Instant now) {
        this.guildId = guildId;
        this.discordId = discordId;
        this.grantedBy = grantedBy;
        this.grantedAt = now;
    }

    public long guildId() {
        return guildId;
    }

    public String discordId() {
        return discordId;
    }

    public String grantedBy() {
        return grantedBy;
    }

    public Instant grantedAt() {
        return grantedAt;
    }
}
