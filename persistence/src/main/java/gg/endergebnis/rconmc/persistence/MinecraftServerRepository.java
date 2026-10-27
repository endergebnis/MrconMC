package gg.endergebnis.rconmc.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Alle Abfragen sind nach {@code guildId} gefiltert - ein Server einer Guild ist fuer andere nie erreichbar. */
public interface MinecraftServerRepository extends JpaRepository<MinecraftServer, Long> {

    List<MinecraftServer> findByGuildIdOrderByIdAsc(long guildId);

    List<MinecraftServer> findByGuildIdAndEnabledTrueOrderByIdAsc(long guildId);

    Optional<MinecraftServer> findByGuildIdAndNameIgnoreCase(long guildId, String name);

    long countByGuildId(long guildId);
}
