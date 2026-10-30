package gg.endergebnis.rconmc.discord.modal;

import java.util.concurrent.ExecutorService;

import gg.endergebnis.rconmc.core.gateway.ServerType;
import gg.endergebnis.rconmc.discord.GuildAdminCheck;
import gg.endergebnis.rconmc.whitelist.server.AddServerResult;
import gg.endergebnis.rconmc.whitelist.server.ServerService;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Das Formular von {@code /server add} (RCON-Passwort oder Management-Secret). Discord kann Modal-Felder nicht maskieren: Das Passwort ist beim Tippen
 * sichtbar, bleibt aber ausserhalb des Chat-Verlaufs und wird weder geloggt noch in der Antwort wiederholt.
 */
@Component
public class ServerAddModalAction {

    private static final Logger log = LoggerFactory.getLogger(ServerAddModalAction.class);

    public static final String MODAL_ID = "server:add";
    static final String INPUT_NAME = "server_name";
    static final String INPUT_HOST = "server_host";
    static final String INPUT_PORT = "server_port";
    static final String INPUT_SECRET = "server_secret";

    private final GuildAdminCheck adminCheck;
    private final ServerService serverService;
    private final ExecutorService botExecutor;

    public ServerAddModalAction(GuildAdminCheck adminCheck, ServerService serverService, ExecutorService botExecutor) {
        this.adminCheck = adminCheck;
        this.serverService = serverService;
        this.botExecutor = botExecutor;
    }

    /** Das Formular zum Servertyp; der Typ steckt in der Modal-ID, damit {@link #handle} ihn wieder auslesen kann. */
    public static Modal modal(ServerType type) {
        boolean management = type.isManagementProtocol();
        TextInput name = TextInput.create(INPUT_NAME, TextInputStyle.SHORT)
                .setPlaceholder("z. B. survival")
                .setRequiredRange(1, 32)
                .build();
        TextInput host = TextInput.create(INPUT_HOST, TextInputStyle.SHORT)
                .setPlaceholder("mc.example.org")
                .setRequiredRange(1, 253)
                .build();
        TextInput.Builder port = TextInput.create(INPUT_PORT, TextInputStyle.SHORT).setRequiredRange(1, 5);
        TextInput.Builder secret = TextInput.create(INPUT_SECRET, TextInputStyle.SHORT).setRequiredRange(1, 256);
        if (management) {
            port.setPlaceholder("management-server-port aus der server.properties");
            secret.setPlaceholder("management-server-secret aus der server.properties");
        } else {
            port.setValue("25575");
            secret.setPlaceholder("rcon.password aus der server.properties");
        }
        return Modal.create(MODAL_ID + ":" + type.name(), title(type))
                .addComponents(
                        Label.of("Name", name),
                        Label.of("Host", host),
                        Label.of(management ? "Management-Port" : "RCON-Port", port.build()),
                        Label.of(management ? "Management-Secret" : "RCON-Passwort", secret.build()))
                .build();
    }

    private static String title(ServerType type) {
        return switch (type) {
            case RCON -> "Server hinzufügen: RCON";
            case MGMT_WS -> "Server hinzufügen: Management";
            case MGMT_WSS -> "Server hinzufügen: Management (TLS)";
        };
    }

    /** @return ob die Modal-ID zu diesem Formular gehoert (egal fuer welchen Servertyp) */
    public static boolean matches(String modalId) {
        return modalId.equals(MODAL_ID) || modalId.startsWith(MODAL_ID + ":");
    }

    /** Servertyp aus der Modal-ID; eine ID ohne Typ (aeltere Formulare) bedeutet RCON. */
    static ServerType typeOf(String modalId) {
        String prefix = MODAL_ID + ":";
        if (!modalId.startsWith(prefix)) {
            return ServerType.RCON;
        }
        try {
            return ServerType.valueOf(modalId.substring(prefix.length()));
        } catch (IllegalArgumentException e) {
            return ServerType.RCON;
        }
    }

    public void handle(ModalInteractionEvent event) {
        Member member = event.getMember();
        if (event.getGuild() == null || member == null) {
            event.reply("Dieser Befehl funktioniert nur auf dem Server.").setEphemeral(true).queue();
            return;
        }
        if (!adminCheck.isAdmin(member)) {
            event.reply("Keine Berechtigung.").setEphemeral(true).queue();
            return;
        }
        long guildId = event.getGuild().getIdLong();
        ServerType type = typeOf(event.getModalId());
        String name = value(event, INPUT_NAME);
        String host = value(event, INPUT_HOST);
        String portText = value(event, INPUT_PORT);
        String secret = value(event, INPUT_SECRET);

        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            event.reply("Der Port muss eine Zahl sein.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();
        botExecutor.execute(() -> {
            String reply;
            try {
                reply = message(serverService.add(guildId, name, type, host, port, secret));
            } catch (RuntimeException e) {
                log.error("Server konnte nicht hinzugefuegt werden", e);
                reply = "Interner Fehler aufgetreten. Details stehen im Log.";
            }
            event.getHook().editOriginal(reply).queue();
        });
    }

    private static String value(ModalInteractionEvent event, String id) {
        ModalMapping mapping = event.getValue(id);
        return mapping == null ? "" : mapping.getAsString().trim();
    }

    static String message(AddServerResult result) {
        return switch (result) {
            case AddServerResult.Added r ->
                    "✅ Server **%s** hinzugefügt. Verbindung erfolgreich getestet.".formatted(r.name());
            case AddServerResult.Invalid r -> r.reason();
            case AddServerResult.LimitReached r ->
                    "Maximale Anzahl von %d Servern erreicht. Entferne zuerst einen mit `/server remove`.".formatted(r.max());
            case AddServerResult.Duplicate r -> "Es gibt bereits einen Server mit dem Namen **%s**.".formatted(r.name());
            case AddServerResult.HostNotAllowed r -> "Host nicht erlaubt: " + r.reason();
            case AddServerResult.ConnectionFailed r ->
                    "Verbindung fehlgeschlagen, Server wurde nicht gespeichert: " + r.reason();
        };
    }
}
