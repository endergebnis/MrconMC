package gg.endergebnis.rconmc.core.verification;

/** Ein verifizierter Minecraft-Account: kanonischer Name und UUID (ohne Bindestriche). */
public record VerifiedAccount(String name, String uuid) {}
