package gg.endergebnis.rconmc.discord.modal;

import java.util.concurrent.ExecutorService;

import gg.endergebnis.rconmc.core.whitelist.RegistrationResult;
import gg.endergebnis.rconmc.discord.button.RegisterButton;
import gg.endergebnis.rconmc.whitelist.WhitelistService;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Verarbeitet das abgeschickte Registrierungs-Modal: Antwort sofort zurueckstellen (ephemeral), die langsame Arbeit
 * (Mojang-API, Server-Aufrufe) auf dem Bot-Executor erledigen und das Ergebnis in die zurueckgestellte Antwort schreiben.
 */
@Component
public class RegisterModalAction {

    private static final Logger log = LoggerFactory.getLogger(RegisterModalAction.class);

    static final String MSG_NO_NAME = "Bitte gib einen Namen an.";
    static final String MSG_INTERNAL_ERROR = "Interner Fehler aufgetreten. Bitte versuche es erneut.";

    private final WhitelistService whitelistService;
    private final ExecutorService botExecutor;

    public RegisterModalAction(WhitelistService whitelistService, ExecutorService botExecutor) {
        this.whitelistService = whitelistService;
        this.botExecutor = botExecutor;
    }

    public void handle(ModalInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("Dieser Befehl funktioniert nur auf dem Server.").setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();

        long guildId = event.getGuild().getIdLong();
        ModalMapping input = event.getValue(RegisterButton.INPUT_NAME);
        String name = input == null ? "" : input.getAsString().trim();
        String discordId = event.getUser().getId();
        String discordUsername = event.getUser().getName();

        botExecutor.execute(() -> {
            String reply;
            if (name.isEmpty()) {
                reply = MSG_NO_NAME;
            } else {
                try {
                    reply = message(whitelistService.register(guildId, discordId, discordUsername, name));
                } catch (RuntimeException e) {
                    log.error("Fehler bei der Registrierung", e);
                    reply = MSG_INTERNAL_ERROR;
                }
            }
            event.getHook().editOriginal(reply).queue();
        });
    }

    static String message(RegistrationResult result) {
        return switch (result) {
            case RegistrationResult.NotFound r -> "Dieser Minecraft-Name existiert nicht.";
            case RegistrationResult.VerificationFailed r ->
                    "Verifizierung fehlgeschlagen, bitte später erneut versuchen.";
            case RegistrationResult.Conflict r ->
                    "Dieser Minecraft-Account ist bereits mit einem anderen Discord-Nutzer verknüpft.";
            case RegistrationResult.Pending r ->
                    "✅ %s gespeichert. Die Whitelist ist gerade geschlossen: Du wirst automatisch hinzugefügt, sobald sie geöffnet wird oder ein Admin dich freigibt."
                            .formatted(r.mcName());
            case RegistrationResult.NoServers r ->
                    "%s gespeichert, aber auf diesem Server ist noch kein Minecraft-Server eingerichtet. Bitte kontaktiere einen Admin."
                            .formatted(r.mcName());
            case RegistrationResult.Whitelisted r -> r.servers() == 1
                    ? "✅ %s wurde zur Whitelist hinzugefügt!".formatted(r.mcName())
                    : "✅ %s wurde auf %d Servern zur Whitelist hinzugefügt!".formatted(r.mcName(), r.servers());
            case RegistrationResult.PartiallyWhitelisted r ->
                    "%s wurde nur teilweise hinzugefügt. Fehlgeschlagen: %s. Bitte kontaktiere einen Admin."
                            .formatted(r.mcName(), String.join(", ", r.failedServers()));
            case RegistrationResult.WhitelistFailed r ->
                    "Registrierung gespeichert, aber der Whitelist-Eintrag schlug fehl. Bitte kontaktiere einen Admin.";
        };
    }
}
