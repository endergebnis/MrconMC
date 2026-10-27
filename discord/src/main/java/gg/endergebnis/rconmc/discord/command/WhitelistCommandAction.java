package gg.endergebnis.rconmc.discord.command;

import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;

import gg.endergebnis.rconmc.core.BatchResult;
import gg.endergebnis.rconmc.discord.GuildAdminCheck;
import gg.endergebnis.rconmc.whitelist.PlayerInfo;
import gg.endergebnis.rconmc.whitelist.WhitelistService;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@code /whitelist}: alle Unterbefehle wirken nur auf die Guild, in der sie ausgefuehrt werden, und sind auf
 * Admins dieser Guild beschraenkt (Pruefung im Handler, damit auch reine Rollen-Admins sie nutzen koennen).
 */
@Component
public class WhitelistCommandAction {

    private static final Logger log = LoggerFactory.getLogger(WhitelistCommandAction.class);

    public static final String COMMAND = "whitelist";
    public static final String SUB_OPEN = "open";
    public static final String SUB_CLOSE = "close";
    public static final String SUB_OPEN_ALL = "open-all";
    public static final String SUB_CLOSE_ALL = "close-all";
    public static final String SUB_USER = "user";
    public static final String SUB_DCUSER = "dcuser";
    public static final String OPTION_NAME = "name";

    private final GuildAdminCheck adminCheck;
    private final WhitelistService whitelistService;
    private final ExecutorService botExecutor;

    public WhitelistCommandAction(
            GuildAdminCheck adminCheck, WhitelistService whitelistService, ExecutorService botExecutor) {
        this.adminCheck = adminCheck;
        this.whitelistService = whitelistService;
        this.botExecutor = botExecutor;
    }

    public static SlashCommandData definition() {
        return Commands.slash(COMMAND, "Verwaltung der Minecraft-Whitelist")
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(
                        new SubcommandData(SUB_OPEN, "Whitelist öffnen und fehlende Spieler hinzufügen"),
                        new SubcommandData(SUB_CLOSE, "Whitelist schließen"),
                        new SubcommandData(SUB_OPEN_ALL, "Alle Spieler auf alle Server dieser Guild setzen"),
                        new SubcommandData(SUB_CLOSE_ALL, "Alle Spieler von allen Servern dieser Guild entfernen"),
                        new SubcommandData(SUB_USER, "Discord-Nutzer zu einem Minecraft-Namen finden")
                                .addOption(OptionType.STRING, OPTION_NAME, "Minecraft-Name", true),
                        new SubcommandData(SUB_DCUSER, "Minecraft-Namen zu einem Discord-Nutzer finden")
                                .addOption(OptionType.STRING, OPTION_NAME, "Discord-Name", true));
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
            case SUB_OPEN -> batch(event, () -> whitelistService.open(guildId),
                    r -> "Whitelist geöffnet. %d/%d Einträge hinzugefügt.".formatted(r.succeeded(), r.total()));
            case SUB_CLOSE -> {
                whitelistService.close(guildId);
                reply(event, "Whitelist geschlossen. Neue Registrierungen werden als 'pending' gespeichert.");
            }
            case SUB_OPEN_ALL -> batch(event, () -> whitelistService.openAll(guildId),
                    r -> "%d/%d Einträge zur Whitelist hinzugefügt.".formatted(r.succeeded(), r.total()));
            case SUB_CLOSE_ALL -> batch(event, () -> whitelistService.closeAll(guildId),
                    r -> "%d/%d Einträge von der Whitelist entfernt.".formatted(r.succeeded(), r.total()));
            case SUB_USER -> lookup(event, true, guildId);
            case SUB_DCUSER -> lookup(event, false, guildId);
            default -> reply(event, "Unbekannter Unterbefehl.");
        }
    }

    /** Sammelaktionen dauern (ein Server-Aufruf je Spieler und Server): Antwort zurueckstellen, auf dem Executor arbeiten. */
    private void batch(
            SlashCommandInteractionEvent event,
            Supplier<BatchResult> action,
            Function<BatchResult, String> message) {
        event.deferReply(true).queue();
        botExecutor.execute(() -> {
            String reply;
            try {
                reply = message.apply(action.get());
            } catch (RuntimeException e) {
                log.error("Sammelaktion fehlgeschlagen", e);
                reply = "Interner Fehler aufgetreten. Details stehen im Log.";
            }
            event.getHook().editOriginal(reply).queue();
        });
    }

    private void lookup(SlashCommandInteractionEvent event, boolean byMinecraftName, long guildId) {
        OptionMapping option = event.getOption(OPTION_NAME);
        if (option == null) {
            reply(event, "Bitte gib einen Namen an.");
            return;
        }
        String name = option.getAsString();
        var found = byMinecraftName
                ? whitelistService.findByMcUsername(guildId, name)
                : whitelistService.findByDiscordUsername(guildId, name);
        reply(event, found.map(WhitelistCommandAction::describe)
                .orElse("Kein Eintrag für **%s**.".formatted(name)));
    }

    private static String describe(PlayerInfo p) {
        String servers = p.whitelistedOn().isEmpty() ? "pending" : String.join(", ", p.whitelistedOn());
        return "Minecraft: **%s**\nDiscord: **%s** (`%s`)\nPlatform: %s\nWhitelist auf: %s"
                .formatted(p.mcUsername(), p.discordUsername(), p.discordId(), p.platform(), servers);
    }

    private static void reply(SlashCommandInteractionEvent event, String content) {
        if (event.isAcknowledged()) {
            event.getHook().editOriginal(content).queue();
        } else {
            event.reply(content).setEphemeral(true).queue();
        }
    }
}
