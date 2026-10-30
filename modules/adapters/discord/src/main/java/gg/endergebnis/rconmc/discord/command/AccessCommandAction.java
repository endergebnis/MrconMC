package gg.endergebnis.rconmc.discord.command;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import gg.endergebnis.rconmc.core.whitelist.BatchResult;
import gg.endergebnis.rconmc.discord.GuildAdminCheck;
import gg.endergebnis.rconmc.whitelist.access.AccessService;
import gg.endergebnis.rconmc.whitelist.access.AllowResult;
import gg.endergebnis.rconmc.whitelist.access.AllowedUser;
import gg.endergebnis.rconmc.whitelist.access.RevokeResult;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
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
 * {@code /access}: Freigabeliste fuer die geschlossene Whitelist. Wer hier steht, darf sich auch bei geschlossener
 * Whitelist registrieren und wird beim Schliessen nicht von den Servern entfernt. Nur fuer Admins der jeweiligen
 * Guild (Pruefung im Handler); alle Aktionen wirken ausschliesslich auf die aufrufende Guild.
 */
@Component
public class AccessCommandAction {

    private static final Logger log = LoggerFactory.getLogger(AccessCommandAction.class);

    public static final String COMMAND = "access";
    public static final String SUB_ADD = "add";
    public static final String SUB_REMOVE = "remove";
    public static final String SUB_LIST = "list";
    public static final String OPTION_USER = "user";

    /** Discord begrenzt Nachrichten auf 2000 Zeichen; die Liste wird davor abgeschnitten. */
    private static final int MAX_LIST_CHARS = 1800;

    private final GuildAdminCheck adminCheck;
    private final AccessService accessService;
    private final ExecutorService botExecutor;

    public AccessCommandAction(GuildAdminCheck adminCheck, AccessService accessService, ExecutorService botExecutor) {
        this.adminCheck = adminCheck;
        this.accessService = accessService;
        this.botExecutor = botExecutor;
    }

    public static SlashCommandData definition() {
        return Commands.slash(COMMAND, "Freigaben für die geschlossene Whitelist verwalten")
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(
                        new SubcommandData(SUB_ADD, "Nutzer freigeben (darf auch bei geschlossener Whitelist joinen)")
                                .addOption(OptionType.USER, OPTION_USER, "Discord-Nutzer", true),
                        new SubcommandData(SUB_REMOVE, "Freigabe eines Nutzers zurücknehmen")
                                .addOption(OptionType.USER, OPTION_USER, "Discord-Nutzer", true),
                        new SubcommandData(SUB_LIST, "Alle freigegebenen Nutzer anzeigen"));
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
            case SUB_ADD -> add(event, guildId, member.getId());
            case SUB_REMOVE -> remove(event, guildId);
            case SUB_LIST -> list(event, guildId);
            default -> reply(event, "Unbekannter Unterbefehl.");
        }
    }

    private void add(SlashCommandInteractionEvent event, long guildId, String adminId) {
        User user = requiredUser(event);
        if (user == null) {
            return;
        }
        run(event, () -> message(user, accessService.allow(guildId, user.getId(), adminId)));
    }

    private void remove(SlashCommandInteractionEvent event, long guildId) {
        User user = requiredUser(event);
        if (user == null) {
            return;
        }
        run(event, () -> message(user, accessService.revoke(guildId, user.getId())));
    }

    private void list(SlashCommandInteractionEvent event, long guildId) {
        run(event, () -> describe(accessService.list(guildId)));
    }

    /** Freigeben/Entziehen kann Server-Aufrufe ausloesen: Antwort zurueckstellen und auf dem Executor arbeiten. */
    private void run(SlashCommandInteractionEvent event, Supplier<String> action) {
        event.deferReply(true).queue();
        botExecutor.execute(() -> {
            String reply;
            try {
                reply = action.get();
            } catch (RuntimeException e) {
                log.error("Freigabe-Aktion fehlgeschlagen", e);
                reply = "Interner Fehler aufgetreten. Details stehen im Log.";
            }
            event.getHook().editOriginal(reply).queue();
        });
    }

    private User requiredUser(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(OPTION_USER);
        if (option == null) {
            reply(event, "Bitte wähle einen Nutzer.");
            return null;
        }
        User user = option.getAsUser();
        if (user.isBot()) {
            reply(event, "Bots können nicht freigegeben werden.");
            return null;
        }
        return user;
    }

    static String message(User user, AllowResult result) {
        String mention = user.getAsMention();
        return switch (result) {
            case AllowResult.AlreadyAllowed r -> "%s ist bereits freigegeben.".formatted(mention);
            case AllowResult.AllowedUnregistered r ->
                    "✅ %s ist freigegeben. Der Nutzer wird beim Registrieren sofort eingetragen, auch wenn die Whitelist geschlossen ist."
                            .formatted(mention);
            case AllowResult.AllowedWhileOpen r ->
                    "✅ %s (**%s**) ist freigegeben. Die Whitelist ist offen, der Nutzer bleibt auch nach dem Schließen eingetragen."
                            .formatted(mention, r.mcName());
            case AllowResult.AllowedAndAdded r -> {
                BatchResult batch = r.result();
                if (batch.total() == 0) {
                    yield "✅ %s (**%s**) ist freigegeben. Es ist aber noch kein Minecraft-Server eingerichtet (`/server add`)."
                            .formatted(mention, r.mcName());
                }
                if (batch.succeeded() == batch.total()) {
                    yield "✅ %s (**%s**) ist freigegeben und wurde auf die Server gesetzt.".formatted(mention, r.mcName());
                }
                yield "%s (**%s**) ist freigegeben, aber der Eintrag auf den Servern klappte nur %s. Prüfe `/server test` oder versuche es mit `/whitelist open-all` erneut."
                        .formatted(mention, r.mcName(), progress(batch));
            }
        };
    }

    static String message(User user, RevokeResult result) {
        String mention = user.getAsMention();
        return switch (result) {
            case RevokeResult.NotAllowed r -> "%s hatte keine Freigabe.".formatted(mention);
            case RevokeResult.Revoked r -> "✅ Freigabe von %s entfernt.".formatted(mention);
            case RevokeResult.RevokedAndRemoved r -> {
                BatchResult batch = r.result();
                if (batch.total() == 0) {
                    yield "✅ Freigabe von %s entfernt. Es ist kein Minecraft-Server eingerichtet, auf dem etwas zu entfernen wäre."
                            .formatted(mention);
                }
                if (batch.succeeded() == batch.total()) {
                    yield "✅ Freigabe von %s entfernt, **%s** wurde von den Servern genommen.".formatted(mention, r.mcName());
                }
                yield "Freigabe von %s entfernt, aber **%s** konnte nur auf %s Servern entfernt werden. Prüfe `/server test`."
                        .formatted(mention, r.mcName(), progress(batch));
            }
        };
    }

    private static String progress(BatchResult result) {
        return "%d/%d".formatted(result.succeeded(), result.total());
    }

    static String describe(List<AllowedUser> allowed) {
        if (allowed.isEmpty()) {
            return "Noch niemand freigegeben. Mit `/access add` freigeben.";
        }
        StringBuilder text = new StringBuilder("**Freigegebene Nutzer** (%d):".formatted(allowed.size()));
        int shown = 0;
        for (AllowedUser user : allowed) {
            String line = "\n• <@%s> – %s".formatted(
                    user.discordId(), user.mcName() == null ? "noch nicht registriert" : "**" + user.mcName() + "**");
            if (text.length() + line.length() > MAX_LIST_CHARS) {
                text.append("\n… und %d weitere".formatted(allowed.size() - shown));
                break;
            }
            text.append(line);
            shown++;
        }
        return text.toString();
    }

    private static void reply(SlashCommandInteractionEvent event, String content) {
        if (event.isAcknowledged()) {
            event.getHook().editOriginal(content).queue();
        } else {
            event.reply(content).setEphemeral(true).queue();
        }
    }
}
