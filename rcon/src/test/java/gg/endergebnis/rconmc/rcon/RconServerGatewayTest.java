package gg.endergebnis.rconmc.rcon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;

import gg.endergebnis.rconmc.core.GatewayException;
import gg.endergebnis.rconmc.core.ServerTarget;
import gg.endergebnis.rconmc.core.ServerType;
import org.junit.jupiter.api.Test;

class RconServerGatewayTest {

    private static ServerTarget target(long id, int port, String secret) {
        return new ServerTarget(id, ServerType.RCON, "127.0.0.1", port, secret);
    }

    @Test
    void sendsWhitelistCommandsAndReusesOneConnectionPerServer() throws IOException {
        try (FakeRconServer server = new FakeRconServer("secret", cmd -> List.of("ok"));
                RconServerGateway gateway = new RconServerGateway()) {
            ServerTarget target = target(1, server.port(), "secret");

            gateway.whitelistAdd(target, "Notch");
            gateway.whitelistRemove(target, "Notch");

            assertThat(server.commands).containsExactly("whitelist add Notch", "whitelist remove Notch");
            assertThat(server.connections.get()).isEqualTo(1);
        }
    }

    @Test
    void serversDoNotShareConnections() throws IOException {
        try (FakeRconServer a = new FakeRconServer("pw-a", cmd -> List.of("ok"));
                FakeRconServer b = new FakeRconServer("pw-b", cmd -> List.of("ok"));
                RconServerGateway gateway = new RconServerGateway()) {
            gateway.whitelistAdd(target(1, a.port(), "pw-a"), "Alex");
            gateway.whitelistAdd(target(2, b.port(), "pw-b"), "Steve");

            assertThat(a.commands).containsExactly("whitelist add Alex");
            assertThat(b.commands).containsExactly("whitelist add Steve");
        }
    }

    @Test
    void aChangedPasswordReplacesTheCachedConnection() throws IOException {
        try (FakeRconServer server = new FakeRconServer("new", cmd -> List.of("ok"));
                RconServerGateway gateway = new RconServerGateway()) {
            assertThatThrownBy(() -> gateway.whitelistAdd(target(1, server.port(), "old"), "Notch"))
                    .isInstanceOf(GatewayException.class);

            gateway.whitelistAdd(target(1, server.port(), "new"), "Notch");

            assertThat(server.commands).containsExactly("whitelist add Notch");
        }
    }

    @Test
    void rejectsNamesThatCouldInjectCommands() throws IOException {
        try (FakeRconServer server = new FakeRconServer("secret", cmd -> List.of("ok"));
                RconServerGateway gateway = new RconServerGateway()) {
            assertThatThrownBy(() -> gateway.whitelistAdd(target(1, server.port(), "secret"), "x\nop Notch"))
                    .isInstanceOf(GatewayException.class);
            assertThat(server.commands).isEmpty();
        }
    }

    @Test
    void testConnectionDoesNotCacheAndReportsWrongPasswords() throws IOException {
        try (FakeRconServer server = new FakeRconServer("secret", cmd -> List.of("There are 0 of a max of 20 players online"));
                RconServerGateway gateway = new RconServerGateway()) {
            gateway.testConnection(target(0, server.port(), "secret"));

            assertThatThrownBy(() -> gateway.testConnection(target(0, server.port(), "wrong")))
                    .isInstanceOf(RconException.class);
        }
    }
}
