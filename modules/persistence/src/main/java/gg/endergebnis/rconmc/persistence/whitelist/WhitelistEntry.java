package gg.endergebnis.rconmc.persistence.whitelist;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Ein Eintrag existiert genau dann, wenn der Spieler auf diesem Server per Bot auf die Whitelist gesetzt wurde. */
@Entity
@Table(name = "whitelist_entry")
public class WhitelistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long playerId;

    @Column(nullable = false)
    private long serverId;

    @Column(nullable = false)
    private Instant addedAt;

    protected WhitelistEntry() {}

    public WhitelistEntry(long playerId, long serverId, Instant now) {
        this.playerId = playerId;
        this.serverId = serverId;
        this.addedAt = now;
    }

    public long playerId() {
        return playerId;
    }

    public long serverId() {
        return serverId;
    }
}
