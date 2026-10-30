package gg.endergebnis.rconmc;

import java.nio.file.Path;

import gg.endergebnis.rconmc.core.gateway.ServerType;
import gg.endergebnis.rconmc.crypto.SecretCipher;
import gg.endergebnis.rconmc.management.ManagementServerGateway;
import gg.endergebnis.rconmc.management.TlsSupport;
import gg.endergebnis.rconmc.rcon.RconServerGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Verdrahtet die framework-freien Module {@code crypto}, {@code rcon} und {@code management} als Spring-Beans. */
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

    /** Management Protocol ohne TLS ({@code ws://}, Server mit {@code management-server-tls-enabled=false}). */
    @Bean(destroyMethod = "close")
    ManagementServerGateway managementServerGateway() {
        return new ManagementServerGateway(ServerType.MGMT_WS, null);
    }

    /**
     * Management Protocol mit TLS ({@code wss://}). Selbst signierte Zertifikate lassen sich ueber einen eigenen
     * Truststore vertrauen; ohne Angabe gilt der Standard-Truststore der JVM.
     */
    @Bean(destroyMethod = "close")
    ManagementServerGateway managementTlsServerGateway(
            @Value("${rconmc.management.truststore-path:}") String truststorePath,
            @Value("${rconmc.management.truststore-password:}") String truststorePassword) {
        Path truststore = truststorePath.isBlank() ? null : Path.of(truststorePath);
        return new ManagementServerGateway(ServerType.MGMT_WSS, TlsSupport.sslContext(truststore, truststorePassword));
    }
}
