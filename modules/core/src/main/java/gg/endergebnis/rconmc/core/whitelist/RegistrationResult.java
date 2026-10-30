package gg.endergebnis.rconmc.core.whitelist;

import java.util.List;

/** Ausgang einer Registrierung ueber das Discord-Modal. */
public sealed interface RegistrationResult {

    /** Der Name existiert nicht (oder hat kein gueltiges Format). */
    record NotFound() implements RegistrationResult {}

    /** Die Pruefung gegen Mojang ist fehlgeschlagen. */
    record VerificationFailed() implements RegistrationResult {}

    /** Der Minecraft-Account gehoert in dieser Guild bereits einem anderen Discord-Nutzer. */
    record Conflict() implements RegistrationResult {}

    /** Gespeichert; die Whitelist der Guild ist geschlossen, der Spieler wartet auf {@code /whitelist open}. */
    record Pending(String mcName) implements RegistrationResult {}

    /** Gespeichert, aber die Guild hat noch keinen aktiven Minecraft-Server. */
    record NoServers(String mcName) implements RegistrationResult {}

    /** Gespeichert und auf allen aktiven Servern der Guild auf die Whitelist gesetzt. */
    record Whitelisted(String mcName, int servers) implements RegistrationResult {}

    /** Gespeichert, aber nur auf einem Teil der Server eingetragen. */
    record PartiallyWhitelisted(String mcName, List<String> failedServers) implements RegistrationResult {}

    /** Gespeichert, aber auf keinem Server eingetragen. */
    record WhitelistFailed(String mcName) implements RegistrationResult {}
}
