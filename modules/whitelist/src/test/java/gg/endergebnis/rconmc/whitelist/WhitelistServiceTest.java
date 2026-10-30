package gg.endergebnis.rconmc.whitelist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import gg.endergebnis.rconmc.core.gateway.GatewayException;
import gg.endergebnis.rconmc.core.gateway.MinecraftServerGateway;
import gg.endergebnis.rconmc.core.gateway.ServerTarget;
import gg.endergebnis.rconmc.core.gateway.ServerType;
import gg.endergebnis.rconmc.core.verification.AccountVerifier;
import gg.endergebnis.rconmc.core.verification.VerifiedAccount;
import gg.endergebnis.rconmc.core.whitelist.BatchResult;
import gg.endergebnis.rconmc.core.whitelist.RegistrationResult;
import gg.endergebnis.rconmc.core.whitelist.WhitelistStatus;
import gg.endergebnis.rconmc.persistence.player.Player;
import gg.endergebnis.rconmc.persistence.player.PlayerRepository;
import gg.endergebnis.rconmc.persistence.whitelist.WhitelistEntry;
import gg.endergebnis.rconmc.persistence.whitelist.WhitelistEntryRepository;
import gg.endergebnis.rconmc.whitelist.access.AccessService;
import gg.endergebnis.rconmc.whitelist.guild.GuildConfigService;
import gg.endergebnis.rconmc.whitelist.server.ActiveServer;
import gg.endergebnis.rconmc.whitelist.server.GatewayRouter;
import gg.endergebnis.rconmc.whitelist.server.ServerService;
import gg.endergebnis.rconmc.whitelist.sync.WhitelistSynchronizer;
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
    private final AccessService access = mock(AccessService.class);
    private final WhitelistSynchronizer synchronizer = new WhitelistSynchronizer(entries, servers, router);
    private final WhitelistService service =
            new WhitelistService(players, entries, guilds, access, synchronizer, verifier);

    private static ActiveServer server(long id, String name) {
        return new ActiveServer(id, name, new ServerTarget(id, ServerType.RCON, "127.0.0.1", 25575, "pw"));
    }

    private static Player player(long id, String discordId, String mcName, String uuid) {
        Player player = new Player(GUILD, discordId, Instant.now());
        player.register("dc-" + discordId, mcName, uuid, Instant.now());
        ReflectionTestUtils.setField(player, "id", id); // wie von der Datenbank vergeben
        return player;
    }

    private void verifies(String name, String uuid) {
        when(verifier.verify(name)).thenReturn(Optional.of(new VerifiedAccount(name, uuid)));
        when(players.save(any(Player.class))).thenAnswer(inv -> {
            Player saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 7L);
            return saved;
        });
    }

    // ---- Registrierung -------------------------------------------------------------------------------

    @Test
    void conflictIsCheckedWithinTheGuildOnly() {
        verifies("Notch", "uuid-1");
        when(players.existsByGuildIdAndMcUuidAndDiscordIdNot(GUILD, "uuid-1", "1")).thenReturn(true);

        assertThat(service.register(GUILD, "1", "alex", "Notch")).isInstanceOf(RegistrationResult.Conflict.class);
        verify(players, never()).save(any());
    }

    @Test
    void closedWhitelistWithoutAccessOnlyStoresThePlayer() {
        verifies("Notch", "uuid-1");
        when(guilds.status(GUILD)).thenReturn(WhitelistStatus.CLOSED);
        when(access.isAllowed(GUILD, "1")).thenReturn(false);

        assertThat(service.register(GUILD, "1", "alex", "Notch")).isEqualTo(new RegistrationResult.Pending("Notch"));
        verify(gateway, never()).whitelistAdd(any(), any());
    }

    @Test
    void closedWhitelistLetsAllowedUsersThrough() {
        verifies("Notch", "uuid-1");
        when(guilds.status(GUILD)).thenReturn(WhitelistStatus.CLOSED);
        when(access.isAllowed(GUILD, "1")).thenReturn(true);
        when(servers.activeServers(GUILD)).thenReturn(List.of(server(1, "a")));
        when(router.get(ServerType.RCON)).thenReturn(gateway);

        assertThat(service.register(GUILD, "1", "alex", "Notch"))
                .isEqualTo(new RegistrationResult.Whitelisted("Notch", 1));
        verify(gateway).whitelistAdd(server(1, "a").target(), "Notch");
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
        verify(entries, times(2)).save(any(WhitelistEntry.class));
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
    void changingTheMinecraftAccountRemovesTheOldNameFromTheServers() {
        Player existing = player(7L, "1", "OldName", "uuid-old");
        verifies("NewName", "uuid-new");
        when(players.findByGuildIdAndDiscordId(GUILD, "1")).thenReturn(Optional.of(existing));
        when(guilds.status(GUILD)).thenReturn(WhitelistStatus.CLOSED);
        when(servers.activeServers(GUILD)).thenReturn(List.of(server(1, "a")));
        when(router.get(ServerType.RCON)).thenReturn(gateway);

        assertThat(service.register(GUILD, "1", "alex", "NewName"))
                .isEqualTo(new RegistrationResult.Pending("NewName"));
        verify(gateway).whitelistRemove(server(1, "a").target(), "OldName");
        verify(entries).deleteByPlayer(7L);
    }

    // ---- Admin-Aktionen ------------------------------------------------------------------------------

    @Test
    void batchSkipsTheRestOfADeadServerButCountsItAsFailed() {
        Player first = player(1L, "1", "Alex", "u1");
        Player second = player(2L, "2", "Steve", "u2");
        when(servers.activeServers(GUILD)).thenReturn(List.of(server(1, "a")));
        when(players.findMissingOnServer(anyLong(), anyLong())).thenReturn(List.of(first, second));
        when(router.get(ServerType.RCON)).thenReturn(gateway);
        doThrow(new GatewayException("down")).when(gateway).whitelistAdd(any(), any());

        assertThat(service.open(GUILD)).isEqualTo(new BatchResult(0, 2));
        verify(guilds).setStatus(GUILD, WhitelistStatus.OPEN);
        verify(gateway, times(1)).whitelistAdd(any(), any());
    }

    @Test
    void closingRemovesPlayersWithoutAccessFromTheServers() {
        Player stranger = player(1L, "1", "Alex", "u1");
        when(players.findWithoutBypass(GUILD)).thenReturn(List.of(stranger));
        when(servers.activeServers(GUILD)).thenReturn(List.of(server(1, "a")));
        when(router.get(ServerType.RCON)).thenReturn(gateway);

        assertThat(service.close(GUILD, true)).isEqualTo(new BatchResult(1, 1));
        verify(guilds).setStatus(GUILD, WhitelistStatus.CLOSED);
        verify(gateway).whitelistRemove(server(1, "a").target(), "Alex");
        verify(entries).deleteByPlayerAndServer(1L, 1L);
    }

    @Test
    void closingWithoutRemovalOnlyChangesTheStatus() {
        assertThat(service.close(GUILD, false)).isEqualTo(new BatchResult(0, 0));
        verify(guilds).setStatus(GUILD, WhitelistStatus.CLOSED);
        verify(players, never()).findWithoutBypass(anyLong());
        verify(gateway, never()).whitelistRemove(any(), any());
    }
}
