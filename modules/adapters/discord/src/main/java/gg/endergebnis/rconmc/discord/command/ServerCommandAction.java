package gg.endergebnis.rconmc.discord.command;

import java.util.List;
import java.util.concurrent.ExecutorService;

import gg.endergebnis.rconmc.core.gateway.ServerType;
import gg.endergebnis.rconmc.discord.GuildAdminCheck;
import gg.endergebnis.rconmc.discord.modal.ServerAddModalAction;
import gg.endergebnis.rconmc.whitelist.server.ServerService;
import gg.endergebnis.rconmc.whitelist.server.ServerSummary;
import gg.endergebnis.rconmc.whitelist.server.TestResult;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@code /server}: Guild-Admins verwalten ihre eigenen Minecraft-Server. {@code add} oeffnet ein Modal, damit das
 * RCON-Passwort bzw. Management-Secret nie als Command-Option im Chat-Verlauf landet. Alle Operationen sind auf die aufrufende Guild begrenzt.
 */
@Component
public class ServerCommandAction {

    private static final Logger log = LoggerFactory.getLogger(ServerCommandAction.class);

    public static final String COMMAND = "server";
    public static final String SUB_ADD = "add";
    public static final String SUB_REMOVE = "remove";
    public static final String SUB_LIST = "list";
    public static final String SUB_TEST = "test";
    public static final String OPTION_NAME = "name";
    public static final String OPTION_TYPE = "type";
    static final String TYPE_RCON = "rcon";
    static final String TYPE_MANAGEMENT = "management";
    static final String TYPE_MANAGEMENT_TLS = "management-tls";

    private final GuildAdminCheck adminCheck;
    private final ServerService serverService;
    private final ExecutorService botExecutor;

    public ServerCommandAction(GuildAdminCheck adminCheck, ServerService serverService, ExecutorService botExecutor) {
        this.adminCheck = adminCheck;
        this.serverService = serverService;
        this.botExecutor = botExecutor;
    }

    public static SlashCommandData definition() {
        return Commands.slash(COMMAND, "Minecraft-Server dieses Discord-Servers verwalten")
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(
                        new SubcommandData(SUB_ADD, "Server hinzufügen (Eingabe per Formular)")
                                .addOptions(new OptionData(OptionType.STRING, OPTION_TYPE, "Verbindungsart (Standard: RCON)")
                                        .addChoices(
                                                new Command.Choice("RCON", TYPE_RCON),
                                                new Command.Choice("Management Protocol (ohne TLS)", TYPE_MANAGEMENT),
                                                new Command.Choice("Management Protocol (mit TLS)", TYPE_MANAGEMENT_TLS))),
                        new SubcommandData(SUB_REMOVE, "Server entfernen")
                                .addOption(OptionType.STRING, OPTION_NAME, "Name des Servers", true),
                        new SubcommandData(SUB_LIST, "Server auflisten"),
                        new SubcommandData(SUB_TEST, "Verbindung zu einem Server testen")
                                .addOption(OptionType.STRING, OPTION_NAME, "Name des Servers", true));
    }

    public void handle(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (event.getGuild() == null || member == null) {
            reply(event, "Dieser Befehl funktioniert nur auf dem Server.");
            return;
        }
        if (!adminCheck.isAdmin(member)) {
            reply(event, "Keine Berechtigung.");
            return;
        }
        long guildId = event.getGuild().getIdLong();
        String sub = event.getSubcommandName();
        if (sub == null) {
            reply(event, "Unbekannter Unterbefehl.");
            return;
        }
        switch (sub) {
            case SUB_ADD -> event.replyModal(ServerAddModalAction.modal(serverType(event))).queue();
            case SUB_REMOVE -> remove(event, guildId);
            case SUB_LIST -> list(event, guildId);
            case SUB_TEST -> test(event, guildId);
            default -> reply(event, "Unbekannter Unterbefehl.");
        }
    }

    private void remove(SlashCommandInteractionEvent event, long guildId) {
        String name = requiredName(event);
        if (name == null) {
            return;
        }
        event.deferReply(true).queue();
        botExecutor.execute(() -> {
            String reply;
            try {
                reply = serverService.remove(guildId, name)
                        ? "Server **%s** wurde entfernt.".formatted(name)
                        : "Kein Server mit dem Namen **%s**.".formatted(name);
            } catch (RuntimeException e) {
                log.error("Server konnte nicht entfernt werden", e);
                reply = "Interner Fehler aufgetreten. Details stehen im Log.";
            }
            event.getHook().editOriginal(reply).queue();
        });
    }

    private void list(SlashCommandInteractionEvent event, long guildId) {
        event.deferReply(true).queue();
        botExecutor.execute(() -> {
            String reply;
            try {
                List<ServerSummary> servers = serverService.list(guildId);
                reply = servers.isEmpty()
                        ? "Noch keine Server eingerichtet. Mit `/server add` hinzufügen."
                        : servers.stream()
                                .map(s -> "• **%s** (`%s:%d`, %s)%s".formatted(
                                        s.name(), s.host(), s.port(), s.type(), s.enabled() ? "" : " – deaktiviert"))
                                .reduce((a, b) -> a + "\n" + b)
                                .orElse("");
            } catch (RuntimeException e) {
                log.error("Serverliste konnte nicht geladen werden", e);
                reply = "Interner Fehler aufgetreten. Details stehen im Log.";
            }
            event.getHook().editOriginal(reply).queue();
        });
    }

    private void test(SlashCommandInteractionEvent event, long guildId) {
        String name = requiredName(event);
        if (name == null) {
            return;
        }
        event.deferReply(true).queue();
        botExecutor.execute(() -> {
            String reply;
            try {
                reply = switch (serverService.test(guildId, name)) {
                    case TestResult.NotFound r -> "Kein Server mit dem Namen **%s**.".formatted(name);
                    case TestResult.Ok r -> "✅ Verbindung zu **%s** erfolgreich.".formatted(name);
                    case TestResult.Failed r -> "Verbindung zu **%s** fehlgeschlagen: %s".formatted(name, r.reason());
                };
            } catch (RuntimeException e) {
                log.error("Verbindungstest fehlgeschlagen", e);
                reply = "Interner Fehler aufgetreten. Details stehen im Log.";
            }
            event.getHook().editOriginal(reply).queue();
        });
    }

    private static ServerType serverType(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(OPTION_TYPE);
        String value = option == null ? TYPE_RCON : option.getAsString();
        return switch (value) {
            case TYPE_MANAGEMENT -> ServerType.MGMT_WS;
            case TYPE_MANAGEMENT_TLS -> ServerType.MGMT_WSS;
            default -> ServerType.RCON;
        };
    }

    private static String requiredName(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(OPTION_NAME);
        if (option == null || option.getAsString().isBlank()) {
            reply(event, "Bitte gib einen Servernamen an.");
            return null;
        }
        return option.getAsString().trim();
    }

    private static void reply(SlashCommandInteractionEvent event, String content) {
        if (event.isAcknowledged()) {
            event.getHook().editOriginal(content).queue();
        } else {
            event.reply(content).setEphemeral(true).queue();
        }
    }
}
