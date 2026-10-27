package gg.endergebnis.rconmc.whitelist;

import gg.endergebnis.rconmc.core.ServerTarget;

/** Ein aktiver Server mit entschluesselten Verbindungsdaten; nur fuer die interne Fachlogik. */
public record ActiveServer(long id, String name, ServerTarget target) {}
