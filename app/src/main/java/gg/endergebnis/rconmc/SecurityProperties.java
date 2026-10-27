package gg.endergebnis.rconmc;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Master-Key fuer die AES-Verschluesselung der RCON-Passwoerter (base64, 32 Byte). Nie in DB oder Git ablegen. */
@ConfigurationProperties(prefix = "rconmc.security")
@Validated
public record SecurityProperties(@NotBlank String masterKey) {

    @Override
    public String toString() {
        return "SecurityProperties[masterKey=[REDACTED]]";
    }
}
