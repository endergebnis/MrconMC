package gg.endergebnis.rconmc.discord.listener;

import gg.endergebnis.rconmc.discord.button.RegisterButton;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

/** Verteilt Button-Interaktionen anhand der Custom-ID; unbekannte IDs werden ignoriert. */
@Component
public class OnButtonInteractionListener extends ListenerAdapter {

    private final RegisterButton registerButton;

    public OnButtonInteractionListener(RegisterButton registerButton) {
        this.registerButton = registerButton;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        switch (event.getComponentId()) {
            case RegisterButton.BUTTON_ID -> registerButton.handle(event);
            default -> {
                // Nicht unser Button.
            }
        }
    }
}
