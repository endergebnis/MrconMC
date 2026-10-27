package gg.endergebnis.rconmc.persistence;

import gg.endergebnis.rconmc.core.WhitelistStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Konfiguration einer Discord-Guild (Setup-System nach HolonetBridge): Kanal und ID der persistenten
 * Registrierungsnachricht, optionale Admin-Rolle und der Whitelist-Status der Guild.
 */
@Entity
@Table(name = "guild_config")
public class GuildConfig {

    @Id
    private Long guildId;

    private Long channelId;
    private Long messageId;
    private Long adminRoleId;
    private String registrationMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WhitelistStatus whitelistStatus = WhitelistStatus.CLOSED;

    protected GuildConfig() {}

    public GuildConfig(long guildId) {
        this.guildId = guildId;
    }

    public void configure(long channelId, long messageId, Long adminRoleId, String registrationMessage) {
        this.channelId = channelId;
        this.messageId = messageId;
        this.adminRoleId = adminRoleId;
        this.registrationMessage = registrationMessage;
    }

    public void whitelistStatus(WhitelistStatus status) {
        this.whitelistStatus = status;
    }

    public long guildId() {
        return guildId;
    }

    public Long channelId() {
        return channelId;
    }

    public Long messageId() {
        return messageId;
    }

    public Long adminRoleId() {
        return adminRoleId;
    }

    public String registrationMessage() {
        return registrationMessage;
    }

    public WhitelistStatus whitelistStatus() {
        return whitelistStatus;
    }
}
