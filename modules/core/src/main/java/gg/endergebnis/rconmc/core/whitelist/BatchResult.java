package gg.endergebnis.rconmc.core.whitelist;

/** Ergebnis einer Sammelaktion: {@code succeeded} von {@code total} Spieler-Server-Paaren wurden verarbeitet. */
public record BatchResult(int succeeded, int total) {}
