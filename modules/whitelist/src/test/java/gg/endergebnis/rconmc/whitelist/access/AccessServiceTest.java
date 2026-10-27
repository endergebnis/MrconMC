package gg.endergebnis.rconmc.whitelist.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import gg.endergebnis.rconmc.core.BatchResult;
import gg.endergebnis.rconmc.core.MinecraftServerGateway;
import gg.endergebnis.rconmc.core.ServerTarget;
import gg.endergebnis.rconmc.core.ServerType;
import gg.endergebnis.rconmc.core.WhitelistStatus;
import gg.endergebnis.rconmc.persistence.player.Player;
import gg.endergebnis.rconmc.persistence.player.PlayerRepository;
import gg.endergebnis.rconmc.persistence.whitelist.WhitelistBypass;
import gg.endergebnis.rconmc.persistence.whitelist.WhitelistBypassRepository;
import gg.endergebnis.rconmc.persistence.whitelist.WhitelistEntryRepository;
import gg.endergebnis.rconmc.whitelist.guild.GuildConfigService;
import gg.endergebnis.rconmc.whitelist.server.ActiveServer;
import gg.endergebnis.rconmc.whitelist.server.GatewayRouter;
import gg.endergebnis.rconmc.whitelist.server.ServerService;
import gg.endergebnis.rconmc.whitelist.sync.WhitelistSynchronizer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AccessServiceTest {

    private static final long GUILD = 42L;
    private static final ActiveServer SERVER =
            new ActiveServer(1, "a", new ServerTarget(1, ServerType.RCON, "127.0.0.1", 25575, "pw"));

    private final WhitelistBypassRepository bypasses = mock(WhitelistBypassRepository.class);
    private final PlayerRepository players = mock(PlayerRepository.class);
    private final GuildConfigService guilds = mock(GuildConfigService.class);
    private final ServerService servers = mock(ServerService.class);
    private final GatewayRouter router = mock(GatewayRouter.class);
    private final MinecraftServerGateway gateway = mock(MinecraftServerGateway.class);
    private final WhitelistEntryRepository entries = mock(WhitelistEntryRepository.class);
    private final AccessService service = new AccessService(
            bypasses, players, guilds, new WhitelistSynchronizer(entries, servers, router));

    private static Player registered() {
        Player player = new Player(GUILD, "1", Instant.now());
        player.register("alex", "Alex", "uuid-1", Instant.now());
        ReflectionTestUtils.setField(player, "id", 5L);
        return player;
    }

    private void serverIsUp() {
        when(servers.activeServers(GUILD)).thenReturn(List.of(SERVER));
        when(router.get(ServerType.RCON)).thenReturn(gateway);
    }

    @Test
    void allowingTwiceIsReported() {
        when(bypasses.existsByGuildIdAndDiscordId(GUILD, "1")).thenReturn(true);

        assertThat(service.allow(GUILD, "1", "admin")).isInstanceOf(AllowResult.AlreadyAllowed.class);
        verify(bypasses, never()).save(any());
    }

    @Test
    void allowingAnUnregisteredUserOnlyStoresTheGrant() {
        when(players.findByGuildIdAndDiscordId(GUILD, "1")).thenReturn(Optional.empty());

        assertThat(service.allow(GUILD, "1", "admin")).isInstanceOf(AllowResult.AllowedUnregistered.class);
        verify(guilds).getOrCreate(GUILD);
        verify(bypasses).save(any(WhitelistBypass.class));
        verify(gateway, never()).whitelistAdd(any(), any());
    }

    @Test
    void allowingARegisteredUserOnAClosedWhitelistAddsThemRightAway() {
        when(players.findByGuildIdAndDiscordId(GUILD, "1")).thenReturn(Optional.of(registered()));
        when(guilds.status(GUILD)).thenReturn(WhitelistStatus.CLOSED);
        serverIsUp();

        assertThat(service.allow(GUILD, "1", "admin"))
                .isEqualTo(new AllowResult.AllowedAndAdded("Alex", new BatchResult(1, 1)));
        verify(gateway).whitelistAdd(SERVER.target(), "Alex");
    }

    @Test
    void allowingARegisteredUserOnAnOpenWhitelistNeedsNoServerCall() {
        when(players.findByGuildIdAndDiscordId(GUILD, "1")).thenReturn(Optional.of(registered()));
        when(guilds.status(GUILD)).thenReturn(WhitelistStatus.OPEN);

        assertThat(service.allow(GUILD, "1", "admin")).isEqualTo(new AllowResult.AllowedWhileOpen("Alex"));
        verify(gateway, never()).whitelistAdd(any(), any());
    }

    @Test
    void revokingWithoutGrantIsReported() {
        when(bypasses.deleteByGuildAndDiscord(GUILD, "1")).thenReturn(0);

        assertThat(service.revoke(GUILD, "1")).isInstanceOf(RevokeResult.NotAllowed.class);
        verify(gateway, never()).whitelistRemove(any(), any());
    }

    @Test
    void revokingOnAClosedWhitelistTakesThePlayerOffTheServers() {
        when(bypasses.deleteByGuildAndDiscord(GUILD, "1")).thenReturn(1);
        when(players.findByGuildIdAndDiscordId(GUILD, "1")).thenReturn(Optional.of(registered()));
        when(guilds.status(GUILD)).thenReturn(WhitelistStatus.CLOSED);
        serverIsUp();

        assertThat(service.revoke(GUILD, "1"))
                .isEqualTo(new RevokeResult.RevokedAndRemoved("Alex", new BatchResult(1, 1)));
        verify(gateway).whitelistRemove(SERVER.target(), "Alex");
    }

    @Test
    void revokingOnAnOpenWhitelistKeepsThePlayerOnTheServers() {
        when(bypasses.deleteByGuildAndDiscord(GUILD, "1")).thenReturn(1);
        when(players.findByGuildIdAndDiscordId(GUILD, "1")).thenReturn(Optional.of(registered()));
        when(guilds.status(GUILD)).thenReturn(WhitelistStatus.OPEN);

        assertThat(service.revoke(GUILD, "1")).isInstanceOf(RevokeResult.Revoked.class);
        verify(gateway, never()).whitelistRemove(any(), any());
    }
}
