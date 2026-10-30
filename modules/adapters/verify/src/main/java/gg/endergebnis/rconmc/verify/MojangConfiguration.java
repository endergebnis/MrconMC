package gg.endergebnis.rconmc.verify;

import java.net.http.HttpClient;
import java.time.Duration;

import gg.endergebnis.rconmc.core.verification.AccountVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Verdrahtet den {@link MojangVerifier} mit einem RestClient (10 s Timeout wie in der Rust-Vorlage). */
@Configuration
class MojangConfiguration {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Bean
    AccountVerifier mojangVerifier(RestClient.Builder restClientBuilder) {
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        requestFactory.setReadTimeout(TIMEOUT);
        RestClient restClient = restClientBuilder
                .baseUrl("https://api.mojang.com")
                .requestFactory(requestFactory)
                .build();
        return new MojangVerifier(restClient);
    }
}
