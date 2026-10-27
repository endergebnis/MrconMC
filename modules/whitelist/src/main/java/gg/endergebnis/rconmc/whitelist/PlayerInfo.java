package gg.endergebnis.rconmc.whitelist;

import java.util.List;

/**
 * Anzeigedaten eines registrierten Spielers inkl. der Server, auf denen er per Bot eingetragen ist, und ob er bei
 * geschlossener Whitelist freigegeben ist.
 */
public record PlayerInfo(
        String discordId,
        String discordUsername,
        String mcUsername,
        String platform,
        List<String> whitelistedOn,
        boolean allowedWhileClosed) {}
