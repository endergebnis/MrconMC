package gg.endergebnis.rconmc.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import gg.endergebnis.rconmc.core.gateway.GatewayException;
import gg.endergebnis.rconmc.core.gateway.ServerTarget;
import gg.endergebnis.rconmc.core.gateway.ServerType;
import org.junit.jupiter.api.Test;

class ManagementServerGatewayTest {

    private static final String SECRET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMN";

    private static ServerTarget target(long id, int port, String secret) {
        return new ServerTarget(id, ServerType.MGMT_WS, "127.0.0.1", port, secret);
    }

    private static ManagementServerGateway gateway() {
        return new ManagementServerGateway(ServerType.MGMT_WS, null);
    }

    @Test
    void addSendsPositionalPlayerListAndReusesOneConnection() throws IOException {
        try (FakeManagementServer server = new FakeManagementServer(SECRET);
                ManagementServerGateway gateway = gateway()) {
            ServerTarget target = target(1, server.port(), SECRET);

            gateway.whitelistAdd(target, "Notch");
            gateway.whitelistAdd(target, "jeb_");

            assertThat(server.connections.get()).isEqualTo(1);
            assertThat(server.calls).hasSize(2);
            Map<?, ?> first = (Map<?, ?>) Json.parse(server.calls.get(0));
            assertThat(first.get("method")).isEqualTo("minecraft:allowlist/add");
            assertThat(first.get("jsonrpc")).isEqualTo("2.0");
            assertThat(first.get("params")).isEqualTo(List.of(List.of(Map.of("name", "Notch"))));
            assertThat(server.allowlist).extracting(p -> p.get("name")).containsExactly("Notch", "jeb_");
        }
    }

    @Test
    void removeLooksUpTheEntryAndSendsItsId() throws IOException {
        try (FakeManagementServer server = new FakeManagementServer(SECRET);
                ManagementServerGateway gateway = gateway()) {
            ServerTarget target = target(1, server.port(), SECRET);
            gateway.whitelistAdd(target, "Notch");
            gateway.whitelistAdd(target, "Steve");
            String notchId = (String) server.allowlist.get(0).get("id");

            gateway.whitelistRemove(target, "notch"); // Gross-/Kleinschreibung egal

            assertThat(server.allowlist).extracting(p -> p.get("name")).containsExactly("Steve");
            Map<?, ?> remove = (Map<?, ?>) Json.parse(server.calls.getLast());
            assertThat(remove.get("method")).isEqualTo("minecraft:allowlist/remove");
            assertThat(remove.get("params"))
                    .isEqualTo(List.of(List.of(Map.of("name", "Notch", "id", notchId))));
        }
    }

    @Test
    void removingAnUnknownNameIsANoOp() throws IOException {
        try (FakeManagementServer server = new FakeManagementServer(SECRET);
                ManagementServerGateway gateway = gateway()) {
            gateway.whitelistRemove(target(1, server.port(), SECRET), "Ghost");

            assertThat(server.calls).hasSize(1); // nur das Lesen der Liste
        }
    }

    @Test
    void wrongSecretIsReportedAsAuthenticationFailureWithoutLeakingIt() throws IOException {
        try (FakeManagementServer server = new FakeManagementServer(SECRET);
                ManagementServerGateway gateway = gateway()) {
            ServerTarget wrong = target(0, server.port(), "wrong-secret");

            assertThatThrownBy(() -> gateway.testConnection(wrong))
                    .isInstanceOf(ManagementException.class)
                    .hasMessageContaining("Authentifizierung")
                    .hasMessageNotContaining("wrong-secret");
            assertThat(server.rejected.get()).isEqualTo(1); // Auth-Fehler wird nicht wiederholt
        }
    }

    @Test
    void testConnectionReadsTheAllowlistAndDoesNotCache() throws IOException {
        try (FakeManagementServer server = new FakeManagementServer(SECRET);
                ManagementServerGateway gateway = gateway()) {
            gateway.testConnection(target(0, server.port(), SECRET));
            gateway.testConnection(target(0, server.port(), SECRET));

            assertThat(server.connections.get()).isEqualTo(2);
        }
    }

    @Test
    void reconnectsAfterTheServerDroppedTheConnection() throws Exception {
        try (FakeManagementServer server = new FakeManagementServer(SECRET);
                ManagementServerGateway gateway = gateway()) {
            ServerTarget target = target(1, server.port(), SECRET);
            gateway.whitelistAdd(target, "Notch");

            server.dropConnections();
            Thread.sleep(200);
            gateway.whitelistAdd(target, "Steve");

            assertThat(server.connections.get()).isEqualTo(2);
            assertThat(server.allowlist).extracting(p -> p.get("name")).containsExactly("Notch", "Steve");
        }
    }

    @Test
    void aChangedSecretReplacesTheCachedConnection() throws IOException {
        try (FakeManagementServer server = new FakeManagementServer("new-secret");
                ManagementServerGateway gateway = gateway()) {
            assertThatThrownBy(() -> gateway.whitelistAdd(target(1, server.port(), "old-secret"), "Notch"))
                    .isInstanceOf(GatewayException.class);

            gateway.whitelistAdd(target(1, server.port(), "new-secret"), "Notch");

            assertThat(server.allowlist).hasSize(1);
        }
    }

    @Test
    void rejectsNamesThatAreNotValidMinecraftNames() throws IOException {
        try (FakeManagementServer server = new FakeManagementServer(SECRET);
                ManagementServerGateway gateway = gateway()) {
            ServerTarget target = target(1, server.port(), SECRET);

            assertThatThrownBy(() -> gateway.whitelistAdd(target, "x\"}],\"op")).isInstanceOf(GatewayException.class);
            assertThatThrownBy(() -> gateway.whitelistRemove(target, "")).isInstanceOf(GatewayException.class);
            assertThat(server.calls).isEmpty();
        }
    }

    @Test
    void serverErrorsBecomeGatewayExceptions() throws IOException {
        try (FakeManagementServer server = new FakeManagementServer(SECRET);
                ManagementClient client = new ManagementClient("127.0.0.1", server.port(), SECRET, false, null)) {
            assertThatThrownBy(() -> client.call("minecraft:foo/bar", List.of(), 0))
                    .isInstanceOf(ManagementException.class)
                    .hasMessageContaining("-32601")
                    .hasMessageContaining("Method not found");
        }
    }

    @Test
    void unreachableServerFailsWithAGatewayException() {
        try (ManagementServerGateway gateway = gateway()) {
            assertThatThrownBy(() -> gateway.testConnection(target(0, 1, SECRET)))
                    .isInstanceOf(GatewayException.class);
        }
    }

    @Test
    void onlyManagementTypesAreAccepted() {
        assertThatThrownBy(() -> new ManagementServerGateway(ServerType.RCON, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new ManagementServerGateway(ServerType.MGMT_WSS, null).type()).isEqualTo(ServerType.MGMT_WSS);
    }
}
