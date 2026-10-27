package gg.endergebnis.rconmc.whitelist;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Prueft Hosts, die ein Guild-Admin eintraegt. Greift nur bei {@code allow-private-hosts=false}. Die Aufloesung
 * passiert hier und spaeter beim Verbinden erneut, DNS-Rebinding ist damit nicht ausgeschlossen - im oeffentlichen
 * Betrieb zusaetzlich per Firewall (Egress) absichern.
 */
@Component
public class HostPolicy {

    private final ServerLimits limits;

    public HostPolicy(ServerLimits limits) {
        this.limits = limits;
    }

    /** @return einen Ablehnungsgrund, oder leer wenn der Host erlaubt ist */
    public Optional<String> check(String host) {
        if (limits.allowPrivateHosts()) {
            return Optional.empty();
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isInternal(address)) {
                    return Optional.of("Adressen in privaten oder lokalen Netzen sind nicht erlaubt.");
                }
            }
        } catch (UnknownHostException e) {
            return Optional.of("Der Host konnte nicht aufgelöst werden.");
        }
        return Optional.empty();
    }

    static boolean isInternal(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet6Address) {
            return (b[0] & 0xFE) == 0xFC; // fc00::/7 (ULA)
        }
        if (a instanceof Inet4Address) {
            return (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 64; // 100.64.0.0/10 (CGNAT)
        }
        return false;
    }
}
