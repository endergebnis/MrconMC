package gg.endergebnis.rconmc.whitelist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import gg.endergebnis.rconmc.core.AccountVerifier;
import gg.endergebnis.rconmc.core.BatchResult;
import gg.endergebnis.rconmc.core.GatewayException;
import gg.endergebnis.rconmc.core.MinecraftServerGateway;
import gg.endergebnis.rconmc.core.RegistrationResult;
import gg.endergebnis.rconmc.core.ServerTarget;
import gg.endergebnis.rconmc.core.ServerType;
import gg.endergebnis.rconmc.core.VerifiedAccount;
import gg.endergebnis.rconmc.core.WhitelistStatus;
import gg.endergebnis.rconmc.persistence.Player;
import gg.endergebnis.rconmc.persistence.PlayerRepository;
import gg.endergebnis.rconmc.persistence.WhitelistEntry;
import gg.endergebnis.rconmc.persistence.WhitelistEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class WhitelistServiceTest {

    private static final long GUILD = 42L;

    private final PlayerRepository players = mock(PlayerRepository.class);
    private final WhitelistEntryRepository entries = mock(WhitelistEntryRepository.class);
    private final GuildConfigService guilds = mock(GuildConfigService.class);
    private final ServerService servers = mock(ServerService.class);
    private final GatewayRouter router = mock(GatewayRouter.class);
    private final MinecraftServerGateway gateway = mock(MinecraftServerGateway.class);
    private final AccountVerifier verifier = mock(AccountVerifier.class);
    private final WhitelistService service =
            new WhitelistService(players, entries, guilds, servers, router, verifier);

    private static ActiveServer server(long id, String name) {
        return new ActiveServer(id, name, new ServerTarget(id, ServerType.RCON, "127.0.0.1", 25575, "pw"));
    }

    private void verifies(String name, String uuid) {
        when(verifier.verify(name)).thenReturn(Optional.of(new VerifiedAccount(name, uuid)));
        when(players.save(any(Player.class))).thenAnswer(inv -> {
            Player saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 7L); // wie von der Datenbank vergeben
            return saved;
        });
    }

    @Test
    void conflictIsCheckedWithinTheGuildOnly() {
        verifies("Notch", "uuid-1");
        when(players.existsByGuildIdAndMcUuidAndDiscordIdNot(GUILD, "uuid-1", "1")).thenReturn(true);

        assertThat(service.register(GUILD, "1", "alex", "Notch")).isInstanceOf(RegistrationResult.Conflict.class);
        verify(players, never()).save(any());
    }

    @Test
    void closedWhitelistOnlyStoresThePlayer() {
        verifies("Notch", "uuid-1");
        when(guilds.status(GUILD)).thenReturn(WhitelistStatus.CLOSED);

        assertThat(service.register(GUILD, "1", "alex", "Notch")).isEqualTo(new RegistrationResult.Pending("Notch"));
        verify(gateway, never()).whitelistAdd(any(), any());
    }

    @Test
    void openWhitelistWithoutServersIsReported() {
        verifies("Notch", "uuid-1");
        when(guilds.status(GUILD)).thenReturn(WhitelistStatus.OPEN);
        when(servers.activeServers(GUILD)).thenReturn(List.of());

        assertThat(service.register(GUILD, "1", "alex", "Notch")).isEqualTo(new RegistrationResult.NoServers("Notch"));
    }

    @Test
    void registrationWhitelistsOnEveryActiveServerOfTheGuild() {
        verifies("Notch", "uuid-1");
        when(guilds.status(GUILD)).thenReturn(WhitelistStatus.OPEN);
        when(servers.activeServers(GUILD)).thenReturn(List.of(server(1, "a"), server(2, "b")));
        when(router.get(ServerType.RCON)).thenReturn(gateway);

        assertThat(service.register(GUILD, "1", "alex", "Notch"))
                .isEqualTo(new RegistrationResult.Whitelisted("Notch", 2));
        verify(gateway).whitelistAdd(server(1, "a").target(), "Notch");
        verify(gateway).whitelistAdd(server(2, "b").target(), "Notch");
        verify(entries, org.mockito.Mockito.times(2)).save(any(WhitelistEntry.class));
    }

    @Test
    void oneFailingServerYieldsAPartialResult() {
        verifies("Notch", "uuid-1");
        when(guilds.status(GUILD)).thenReturn(WhitelistStatus.OPEN);
        when(servers.activeServers(GUILD)).thenReturn(List.of(server(1, "a"), server(2, "b")));
        when(router.get(ServerType.RCON)).thenReturn(gateway);
        doThrow(new GatewayException("down")).when(gateway).whitelistAdd(server(2, "b").target(), "Notch");

        assertThat(service.register(GUILD, "1", "alex", "Notch"))
                .isEqualTo(new RegistrationResult.PartiallyWhitelisted("Notch", List.of("b")));
    }

    @Test
    void batchSkipsTheRestOfADeadServerButCountsItAsFailed() {
        Player first = new Player(GUILD, "1", java.time.Instant.now());
        first.register("a", "Alex", "u1", java.time.Instant.now());
        Player second = new Player(GUILD, "2", java.time.Instant.now());
        second.register("b", "Steve", "u2", java.time.Instant.now());
        when(servers.activeServers(GUILD)).thenReturn(List.of(server(1, "a")));
        when(players.findMissingOnServer(anyLong(), anyLong())).thenReturn(List.of(first, second));
        when(router.get(ServerType.RCON)).thenReturn(gateway);
        doThrow(new GatewayException("down")).when(gateway).whitelistAdd(any(), any());

        assertThat(service.open(GUILD)).isEqualTo(new BatchResult(0, 2));
        verify(guilds).setStatus(GUILD, WhitelistStatus.OPEN);
        verify(gateway, org.mockito.Mockito.times(1)).whitelistAdd(any(), any());
    }
}
