package gg.endergebnis.rconmc.discord.listener;

import gg.endergebnis.rconmc.discord.button.RegisterButton;
import gg.endergebnis.rconmc.discord.modal.RegisterModalAction;
import gg.endergebnis.rconmc.discord.modal.ServerAddModalAction;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

/** Verteilt Modal-Abgaben anhand der Modal-ID; unbekannte IDs werden ignoriert. */
@Component
public class OnModalInteractionListener extends ListenerAdapter {

    private final RegisterModalAction registerModal;
    private final ServerAddModalAction serverAddModal;

    public OnModalInteractionListener(RegisterModalAction registerModal, ServerAddModalAction serverAddModal) {
        this.registerModal = registerModal;
        this.serverAddModal = serverAddModal;
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        switch (event.getModalId()) {
            case RegisterButton.MODAL_ID -> registerModal.handle(event);
            case ServerAddModalAction.MODAL_ID -> serverAddModal.handle(event);
            default -> {
                // Nicht unser Modal.
            }
        }
    }
}
