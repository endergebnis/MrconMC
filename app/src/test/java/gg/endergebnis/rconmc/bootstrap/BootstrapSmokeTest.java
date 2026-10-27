package gg.endergebnis.rconmc.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import gg.endergebnis.rconmc.RconMcApplication;
import gg.endergebnis.rconmc.discord.DiscordProperties;
import gg.endergebnis.rconmc.whitelist.GuildConfigService;
import gg.endergebnis.rconmc.whitelist.ServerService;
import gg.endergebnis.rconmc.whitelist.WhitelistService;
import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
        classes = RconMcApplication.class,
        properties = {
            "rconmc.discord.token=test-discord-token",
            // 32 Nullbytes, base64
            "rconmc.security.master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "spring.datasource.url=jdbc:sqlite:target/smoke-test.db?journal_mode=WAL&busy_timeout=5000&foreign_keys=on"
        })
class BootstrapSmokeTest {

    // Ersetzt die echte JDA-Verbindung, damit der Test keinen Discord-Login versucht.
    @MockitoBean
    private JDA jda;

    @Autowired
    private DiscordProperties discordProperties;

    @Autowired
    private GuildConfigService guilds;

    @Autowired
    private ServerService serverService;

    @Autowired
    private WhitelistService whitelistService;

    @Test
    void contextStartsWithFlywaySchemaAndWiredServices() {
        assertThat(discordProperties.toString()).doesNotContain("test-discord-token");
        // beruehrt jede Tabelle ueber die Repositories und beweist damit Schema + Dialekt
        assertThat(serverService.list(1L)).isEmpty();
        assertThat(whitelistService.findByMcUsername(1L, "nobody")).isEmpty();
        assertThat(guilds.status(1L)).isNotNull();
    }
}
