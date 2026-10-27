package gg.endergebnis.rconmc;

import gg.endergebnis.rconmc.crypto.SecretCipher;
import gg.endergebnis.rconmc.rcon.RconServerGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Verdrahtet die framework-freien Module {@code crypto} und {@code rcon} als Spring-Beans. */
@Configuration
class ApplicationConfiguration {

    /** Ein ungueltiger Master-Key bricht den Start ab (fail fast). */
    @Bean
    SecretCipher secretCipher(SecurityProperties properties) {
        return SecretCipher.fromBase64(properties.masterKey());
    }

    @Bean(destroyMethod = "close")
    RconServerGateway rconServerGateway() {
        return new RconServerGateway();
    }
}
