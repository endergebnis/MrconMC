package gg.endergebnis.rconmc.whitelist.server;

import gg.endergebnis.rconmc.core.gateway.ServerTarget;

/** Ein aktiver Server mit entschluesselten Verbindungsdaten; nur fuer die interne Fachlogik. */
public record ActiveServer(long id, String name, ServerTarget target) {}
