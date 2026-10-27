package gg.endergebnis.rconmc.whitelist.sync;

import java.util.List;

/** Ergebnis des Eintragens eines Spielers auf allen aktiven Servern: {@code servers} aktive Server, davon {@code failedServers} ohne Erfolg. */
public record SyncResult(int servers, List<String> failedServers) {}
