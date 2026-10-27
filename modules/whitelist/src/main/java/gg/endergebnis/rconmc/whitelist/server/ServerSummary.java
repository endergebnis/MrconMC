package gg.endergebnis.rconmc.whitelist.server;

import gg.endergebnis.rconmc.core.ServerType;

/** Anzeigedaten eines Servers - bewusst ohne Secret. */
public record ServerSummary(String name, ServerType type, String host, int port, boolean enabled) {}
