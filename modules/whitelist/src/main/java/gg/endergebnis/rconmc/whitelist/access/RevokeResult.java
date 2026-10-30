package gg.endergebnis.rconmc.whitelist.access;

import gg.endergebnis.rconmc.core.whitelist.BatchResult;

/** Ausgang von {@link AccessService#revoke}. */
public sealed interface RevokeResult {

    /** Der Nutzer hatte keine Freigabe. */
    record NotAllowed() implements RevokeResult {}

    /** Freigabe entfernt; auf den Servern war nichts zu tun (Whitelist offen oder Nutzer nicht registriert). */
    record Revoked() implements RevokeResult {}

    /** Freigabe entfernt und der Spieler bei geschlossener Whitelist von den Servern genommen. */
    record RevokedAndRemoved(String mcName, BatchResult result) implements RevokeResult {}
}
