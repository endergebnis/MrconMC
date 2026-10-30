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
        String modalId = event.getModalId();
        if (modalId.equals(RegisterButton.MODAL_ID)) {
            registerModal.handle(event);
        } else if (ServerAddModalAction.matches(modalId)) {
            serverAddModal.handle(event);
        }
        // Alles andere ist nicht unser Modal.
    }
}
