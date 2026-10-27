package gg.endergebnis.rconmc.discord.command;

import java.util.concurrent.ExecutorService;

import gg.endergebnis.rconmc.discord.button.RegisterButton;
import gg.endergebnis.rconmc.whitelist.GuildConfigService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@code /setup} (Setup-System nach HolonetBridge): postet die persistente Registrierungsnachricht in den gewaehlten
 * Kanal und speichert Kanal, Nachricht, Admin-Rolle und Text je Guild. Ein erneuter Aufruf verschiebt die Nachricht
 * (die alte wird nach Moeglichkeit geloescht). Erfordert "Server verwalten" - auch im Handler, falls die
 * Discord-Integrationsrechte des Servers den Command weitergeben.
 */
@Component
public class SetupCommandAction {

    private static final Logger log = LoggerFactory.getLogger(SetupCommandAction.class);

    public static final String COMMAND = "setup";
    public static final String OPTION_CHANNEL = "channel";
    public static final String OPTION_ROLE = "admin-role";
    public static final String OPTION_MESSAGE = "message";

    private static final int EMBED_COLOR = 0x2ECC71;
    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final GuildConfigService guilds;
    private final ExecutorService botExecutor;

    public SetupCommandAction(GuildConfigService guilds, ExecutorService botExecutor) {
        this.guilds = guilds;
        this.botExecutor = botExecutor;
    }

    public static SlashCommandData definition() {
        return Commands.slash(COMMAND, "Richtet die Whitelist-Registrierung für diesen Server ein")
                .addOptions(
                        new OptionData(OptionType.CHANNEL, OPTION_CHANNEL, "Kanal für die Registrierungs-Nachricht", true)
                                .setChannelTypes(ChannelType.TEXT),
                        new OptionData(OptionType.ROLE, OPTION_ROLE, "Rolle mit Admin-Rechten für den Bot (optional)", false),
                        new OptionData(OptionType.STRING, OPTION_MESSAGE, "Text der Registrierungs-Nachricht (optional)", false))
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .setContexts(InteractionContextType.GUILD);
    }

    public void handle(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            event.reply("Dieser Befehl funktioniert nur auf einem Server.").setEphemeral(true).queue();
            return;
        }
        if (!member.hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("Keine Berechtigung.").setEphemeral(true).queue();
            return;
        }
        OptionMapping channelOption = event.getOption(OPTION_CHANNEL);
        if (channelOption == null || channelOption.getChannelType() != ChannelType.TEXT) {
            event.reply("Bitte wähle einen Text-Kanal.").setEphemeral(true).queue();
            return;
        }
        TextChannel channel = channelOption.getAsChannel().asTextChannel();
        Long adminRoleId = event.getOption(OPTION_ROLE, option -> option.getAsRole().getIdLong());
        String customMessage = event.getOption(OPTION_MESSAGE, OptionMapping::getAsString);
        if (customMessage != null && customMessage.length() > MAX_MESSAGE_LENGTH) {
            event.reply("Der Text ist zu lang (max. %d Zeichen).".formatted(MAX_MESSAGE_LENGTH))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        String message = customMessage == null || customMessage.isBlank()
                ? guilds.registrationMessage(guild.getIdLong())
                : customMessage;

        event.deferReply(true).queue();
        channel.sendMessageEmbeds(new EmbedBuilder()
                        .setTitle("Minecraft-Whitelist")
                        .setDescription(message)
                        .setColor(EMBED_COLOR)
                        .build())
                .addComponents(ActionRow.of(Button.primary(RegisterButton.BUTTON_ID, "Registrieren")))
                .queue(
                        sent -> botExecutor.execute(() -> store(event, guild, channel, sent.getIdLong(), adminRoleId, message)),
                        failure -> {
                            log.error("Registrierungs-Nachricht konnte nicht gepostet werden", failure);
                            event.getHook()
                                    .editOriginal("Die Nachricht konnte nicht gepostet werden - fehlen dem Bot die Rechte im Kanal?")
                                    .queue();
                        });
    }

    private void store(
            SlashCommandInteractionEvent event,
            Guild guild,
            TextChannel channel,
            long messageId,
            Long adminRoleId,
            String message) {
        try {
            GuildConfigService.PreviousMessage previous =
                    guilds.configure(guild.getIdLong(), channel.getIdLong(), messageId, adminRoleId, message);
            deleteOldMessage(guild, previous, messageId);
            event.getHook()
                    .editOriginal("Eingerichtet: Die Registrierungs-Nachricht steht jetzt in %s. Nächster Schritt: `/server add`."
                            .formatted(channel.getAsMention()))
                    .queue();
        } catch (RuntimeException e) {
            log.error("Guild-Konfiguration konnte nicht gespeichert werden", e);
            event.getHook().editOriginal("Interner Fehler aufgetreten. Details stehen im Log.").queue();
        }
    }

    private static void deleteOldMessage(Guild guild, GuildConfigService.PreviousMessage previous, long newMessageId) {
        if (previous.channelId() == null || previous.messageId() == null || previous.messageId() == newMessageId) {
            return;
        }
        TextChannel old = guild.getTextChannelById(previous.channelId());
        if (old != null) {
            old.deleteMessageById(previous.messageId()).queue(ok -> {}, failure -> {});
        }
    }
}
