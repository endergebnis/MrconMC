package gg.endergebnis.rconmc.rcon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

class RconClientTest {

    @Test
    void executesACommandAndReturnsTheServerOutput() throws IOException {
        try (FakeRconServer server = new FakeRconServer("secret", cmd -> List.of("Added Notch to the whitelist"))) {
            RconClient client = new RconClient("127.0.0.1", server.port(), "secret");

            assertThat(client.execute("whitelist add Notch", 0)).isEqualTo("Added Notch to the whitelist");
            assertThat(server.commands).containsExactly("whitelist add Notch");
            client.close();
        }
    }

    @Test
    void joinsResponsesThatTheServerSplitsAcrossSeveralPackets() throws IOException {
        try (FakeRconServer server = new FakeRconServer("secret", cmd -> List.of("part-1,", "part-2"))) {
            RconClient client = new RconClient("127.0.0.1", server.port(), "secret");

            assertThat(client.execute("list", 0)).isEqualTo("part-1,part-2");
            client.close();
        }
    }

    @Test
    void reusesOneConnectionForSeveralCommands() throws IOException {
        try (FakeRconServer server = new FakeRconServer("secret", cmd -> List.of("ok"))) {
            RconClient client = new RconClient("127.0.0.1", server.port(), "secret");

            client.execute("one", 0);
            client.execute("two", 0);

            assertThat(server.connections.get()).isEqualTo(1);
            assertThat(server.commands).containsExactly("one", "two");
            client.close();
        }
    }

    @Test
    void failsOnWrongPassword() throws IOException {
        try (FakeRconServer server = new FakeRconServer("secret", cmd -> List.of("ok"))) {
            RconClient client = new RconClient("127.0.0.1", server.port(), "wrong");

            assertThatThrownBy(() -> client.execute("list", 0))
                    .isInstanceOf(RconException.class)
                    .hasMessageContaining("Authentifizierung");
            assertThat(server.commands).isEmpty();
        }
    }

    @Test
    void reconnectsAndRetriesAfterTheConnectionDrops() throws IOException {
        try (FakeRconServer server = new FakeRconServer("secret", cmd -> List.of("ok"))) {
            RconClient client = new RconClient("127.0.0.1", server.port(), "secret");
            server.dropNextConnectionAfterAuth();

            assertThat(client.execute("list", 2)).isEqualTo("ok");
            assertThat(server.connections.get()).isEqualTo(2);
            client.close();
        }
    }

    @Test
    void reportsAnUnreachableServer() {
        RconClient client = new RconClient("127.0.0.1", 1, "secret");

        assertThatThrownBy(() -> client.execute("list", 0)).isInstanceOf(RconException.class);
    }
}
