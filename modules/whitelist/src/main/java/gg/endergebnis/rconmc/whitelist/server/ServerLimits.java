package gg.endergebnis.rconmc.whitelist.server;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Grenzen fuer Server, die Guild-Admins selbst hinzufuegen: maximale Anzahl je Guild und ob Hosts in privaten
 * Netzen (Loopback, RFC1918, Link-Local, ULA, CGNAT) erlaubt sind. {@code allow-private-hosts=false} verhindert, dass
 * der Bot als Scanner fuer das interne Netz des Betreibers missbraucht wird.
 */
@ConfigurationProperties(prefix = "rconmc.servers")
public record ServerLimits(
        @DefaultValue("5") int maxPerGuild,
        @DefaultValue("true") boolean allowPrivateHosts) {}
