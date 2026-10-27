package gg.endergebnis.rconmc.whitelist;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import gg.endergebnis.rconmc.core.GatewayException;
import gg.endergebnis.rconmc.core.MinecraftServerGateway;
import gg.endergebnis.rconmc.core.ServerType;
import org.springframework.stereotype.Component;

/** Waehlt die passende {@link MinecraftServerGateway}-Implementierung anhand des Server-Typs. */
@Component
public class GatewayRouter {

    private final Map<ServerType, MinecraftServerGateway> gateways = new EnumMap<>(ServerType.class);

    public GatewayRouter(List<MinecraftServerGateway> available) {
        available.forEach(gateway -> gateways.put(gateway.type(), gateway));
    }

    public MinecraftServerGateway get(ServerType type) {
        MinecraftServerGateway gateway = gateways.get(type);
        if (gateway == null) {
            throw new GatewayException("Server-Typ " + type + " wird nicht unterstützt");
        }
        return gateway;
    }

    public void evict(long serverId) {
        gateways.values().forEach(gateway -> gateway.evict(serverId));
    }
}
