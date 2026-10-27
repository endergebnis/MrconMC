package gg.endergebnis.rconmc.discord.listener;

import gg.endergebnis.rconmc.discord.command.AccessCommandAction;
import gg.endergebnis.rconmc.discord.command.ServerCommandAction;
import gg.endergebnis.rconmc.discord.command.SetupCommandAction;
import gg.endergebnis.rconmc.discord.command.WhitelistCommandAction;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

/** Leitet {@code /setup}, {@code /whitelist}, {@code /access} und {@code /server} an ihre Actions weiter; andere Commands werden ignoriert. */
@Component
public class OnSlashCommandInteractionListener extends ListenerAdapter {

    private final SetupCommandAction setupCommand;
    private final WhitelistCommandAction whitelistCommand;
    private final AccessCommandAction accessCommand;
    private final ServerCommandAction serverCommand;

    public OnSlashCommandInteractionListener(
            SetupCommandAction setupCommand,
            WhitelistCommandAction whitelistCommand,
            AccessCommandAction accessCommand,
            ServerCommandAction serverCommand) {
        this.setupCommand = setupCommand;
        this.whitelistCommand = whitelistCommand;
        this.accessCommand = accessCommand;
        this.serverCommand = serverCommand;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case SetupCommandAction.COMMAND -> setupCommand.handle(event);
            case WhitelistCommandAction.COMMAND -> whitelistCommand.handle(event);
            case AccessCommandAction.COMMAND -> accessCommand.handle(event);
            case ServerCommandAction.COMMAND -> serverCommand.handle(event);
            default -> {
                // Nicht unser Command.
            }
        }
    }
}
