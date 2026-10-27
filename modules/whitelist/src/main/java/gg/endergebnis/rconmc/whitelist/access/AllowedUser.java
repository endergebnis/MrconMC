package gg.endergebnis.rconmc.whitelist.access;

import java.time.Instant;

/** Anzeigedaten einer Freigabe; {@code mcName} ist {@code null}, solange sich der Nutzer nicht registriert hat. */
public record AllowedUser(String discordId, String mcName, String grantedBy, Instant grantedAt) {}
