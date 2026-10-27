package gg.endergebnis.rconmc.discord.button;

import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.springframework.stereotype.Component;

/**
 * Der persistente "Registrieren"-Button der Registrierungsnachricht (Custom-ID {@code whitelist:register:java}).
 * Die ID ist fuer alle Guilds gleich; die Guild ergibt sich aus der Interaction.
 */
@Component
public class RegisterButton {

    public static final String BUTTON_ID = "whitelist:register:java";
    public static final String MODAL_ID = "whitelist:modal:java";
    public static final String INPUT_NAME = "minecraft_name";

    public void handle(ButtonInteractionEvent event) {
        event.replyModal(registrationModal()).queue();
    }

    private static Modal registrationModal() {
        TextInput name = TextInput.create(INPUT_NAME, TextInputStyle.SHORT)
                .setPlaceholder("Dein Minecraft-Nutzername")
                .setRequiredRange(1, 16)
                .build();
        return Modal.create(MODAL_ID, "Minecraft-Registrierung")
                .addComponents(Label.of("Minecraft-Name", name))
                .build();
    }
}
