package gg.endergebnis.rconmc.whitelist.guild;

import java.util.Optional;

import gg.endergebnis.rconmc.core.whitelist.WhitelistStatus;
import gg.endergebnis.rconmc.persistence.guild.GuildConfig;
import gg.endergebnis.rconmc.persistence.guild.GuildConfigRepository;
import org.springframework.stereotype.Service;

/** Setup-System: Konfiguration je Guild (Kanal, Nachricht, Admin-Rolle, Whitelist-Status). */
@Service
public class GuildConfigService {

    public static final String DEFAULT_REGISTRATION_MESSAGE =
            "Klicke auf den Button, um dich mit deinem Minecraft-Namen zu registrieren.";

    /** Wo die Registrierungsnachricht vor einem erneuten {@code /setup} lag (zum Aufraeumen). */
    public record PreviousMessage(Long channelId, Long messageId) {}

    private final GuildConfigRepository repository;

    public GuildConfigService(GuildConfigRepository repository) {
        this.repository = repository;
    }

    /** Liefert die Konfiguration der Guild und legt sie (Status {@code CLOSED}) an, falls sie noch fehlt. */
    public GuildConfig getOrCreate(long guildId) {
        return repository.findById(guildId).orElseGet(() -> repository.save(new GuildConfig(guildId)));
    }

    public PreviousMessage configure(
            long guildId, long channelId, long messageId, Long adminRoleId, String registrationMessage) {
        GuildConfig config = getOrCreate(guildId);
        PreviousMessage previous = new PreviousMessage(config.channelId(), config.messageId());
        config.configure(channelId, messageId, adminRoleId, registrationMessage);
        repository.save(config);
        return previous;
    }

    public WhitelistStatus status(long guildId) {
        return repository.findById(guildId).map(GuildConfig::whitelistStatus).orElse(WhitelistStatus.CLOSED);
    }

    public void setStatus(long guildId, WhitelistStatus status) {
        GuildConfig config = getOrCreate(guildId);
        config.whitelistStatus(status);
        repository.save(config);
    }

    public Optional<Long> adminRoleId(long guildId) {
        return repository.findById(guildId).map(GuildConfig::adminRoleId);
    }

    public String registrationMessage(long guildId) {
        return repository.findById(guildId)
                .map(GuildConfig::registrationMessage)
                .filter(message -> !message.isBlank())
                .orElse(DEFAULT_REGISTRATION_MESSAGE);
    }
}
