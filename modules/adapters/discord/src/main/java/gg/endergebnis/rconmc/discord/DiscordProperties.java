package gg.endergebnis.rconmc.discord;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Das Bot-Token. Es bleibt bewusst in Env/Docker-Secret und gelangt nie in die Datenbank. */
@ConfigurationProperties(prefix = "rconmc.discord")
@Validated
public record DiscordProperties(@NotBlank String token) {

    @Override
    public String toString() {
        return "DiscordProperties[token=[REDACTED]]";
    }
}
