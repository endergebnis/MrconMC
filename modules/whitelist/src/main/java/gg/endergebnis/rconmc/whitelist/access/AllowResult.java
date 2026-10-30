package gg.endergebnis.rconmc.whitelist.access;

import gg.endergebnis.rconmc.core.whitelist.BatchResult;

/** Ausgang von {@link AccessService#allow}. */
public sealed interface AllowResult {

    /** Der Nutzer war schon freigegeben. */
    record AlreadyAllowed() implements AllowResult {}

    /** Freigegeben; der Nutzer hat sich noch nicht registriert und wird beim Registrieren sofort eingetragen. */
    record AllowedUnregistered() implements AllowResult {}

    /** Freigegeben; die Whitelist ist offen, der registrierte Spieler braucht keine weitere Aktion. */
    record AllowedWhileOpen(String mcName) implements AllowResult {}

    /** Freigegeben und (bei geschlossener Whitelist) sofort auf die Server der Guild gesetzt. */
    record AllowedAndAdded(String mcName, BatchResult result) implements AllowResult {}
}
