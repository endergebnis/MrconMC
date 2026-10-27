package gg.endergebnis.rconmc.discord;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import gg.endergebnis.rconmc.discord.command.ServerCommandAction;
import gg.endergebnis.rconmc.discord.command.SetupCommandAction;
import gg.endergebnis.rconmc.discord.command.WhitelistCommandAction;
import gg.endergebnis.rconmc.discord.listener.OnButtonInteractionListener;
import gg.endergebnis.rconmc.discord.listener.OnModalInteractionListener;
import gg.endergebnis.rconmc.discord.listener.OnSlashCommandInteractionListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Baut die eine JDA-Gateway-Verbindung fuer alle Guilds und registriert die Commands <em>global</em>; welche Guild
 * wie konfiguriert ist, steht in der Datenbank ({@code /setup}). Der Start blockiert bis {@code READY}; ein falsches
 * Token bricht den Anwendungsstart ab (fail fast). Es gibt bewusst keine Guild-ID mehr in der Konfiguration.
 */
@Configuration
public class DiscordGatewayConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DiscordGatewayConfiguration.class);

    /** Fuer langsame, blockierende Arbeit (Mojang-API, RCON, Datenbank), damit JDA-Eventthreads frei bleiben. */
    @Bean(destroyMethod = "close")
    ExecutorService botExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean(destroyMethod = "shutdown")
    JDA jda(
            DiscordProperties discordProperties,
            OnButtonInteractionListener buttonListener,
            OnModalInteractionListener modalListener,
            OnSlashCommandInteractionListener slashCommandListener)
            throws InterruptedException {
        JDA jda = JDABuilder.createLight(discordProperties.token(), Collections.emptyList())
                .addEventListeners(buttonListener, modalListener, slashCommandListener)
                .build()
                .awaitReady();

        log.info("Eingeloggt als {} auf {} Guild(s)", jda.getSelfUser().getName(), jda.getGuilds().size());

        jda.updateCommands()
                .addCommands(
                        SetupCommandAction.definition(),
                        WhitelistCommandAction.definition(),
                        ServerCommandAction.definition())
                .queue(
                        commands -> log.info("{} Slash-Command(s) registriert", commands.size()),
                        failure -> log.error("Slash-Commands konnten nicht registriert werden", failure));
        return jda;
    }
}
