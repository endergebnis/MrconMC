package gg.endergebnis.rconmc.verify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import gg.endergebnis.rconmc.core.VerificationException;
import gg.endergebnis.rconmc.core.VerifiedAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MojangVerifierTest {

    private MockRestServiceServer server;
    private MojangVerifier verifier;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mojang.com");
        server = MockRestServiceServer.bindTo(builder).build();
        verifier = new MojangVerifier(builder.build());
    }

    @Test
    void returnsTheCanonicalNameAndUuid() {
        server.expect(requestTo("https://api.mojang.com/users/profiles/minecraft/notch"))
                .andRespond(withSuccess(
                        "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(verifier.verify("notch")).contains(
                new VerifiedAccount("Notch", "069a79f444e94726a5befca90e38aaf5"));
        server.verify();
    }

    @Test
    void unknownNameIsEmpty() {
        server.expect(requestTo("https://api.mojang.com/users/profiles/minecraft/nobody_here"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(verifier.verify("nobody_here")).isEmpty();
    }

    @Test
    void invalidNamesNeverReachTheNetwork() {
        assertThat(verifier.verify("../etc")).isEmpty();
        assertThat(verifier.verify("name with space")).isEmpty();
        assertThat(verifier.verify("this_name_is_way_too_long")).isEmpty();
        server.verify(); // keine Requests erwartet
    }

    @Test
    void serverErrorsAreVerificationFailuresNotNotFound() {
        server.expect(requestTo("https://api.mojang.com/users/profiles/minecraft/notch"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> verifier.verify("notch")).isInstanceOf(VerificationException.class);
    }
}
