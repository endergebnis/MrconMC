package gg.endergebnis.rconmc.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByGuildIdAndDiscordId(long guildId, String discordId);

    Optional<Player> findFirstByGuildIdAndMcUsernameIgnoreCase(long guildId, String mcUsername);

    Optional<Player> findFirstByGuildIdAndDiscordUsernameIgnoreCase(long guildId, String discordUsername);

    /** {@code true}, wenn ein <em>anderer</em> Discord-Nutzer dieser Guild diese Minecraft-UUID bereits nutzt. */
    boolean existsByGuildIdAndMcUuidAndDiscordIdNot(long guildId, String mcUuid, String discordId);

    List<Player> findByGuildIdOrderByIdAsc(long guildId);

    /** Spieler der Guild, die auf dem Server noch keinen Whitelist-Eintrag haben. */
    @Query("""
            select p from Player p
            where p.guildId = :guildId
              and not exists (select 1 from WhitelistEntry e where e.playerId = p.id and e.serverId = :serverId)
            order by p.id
            """)
    List<Player> findMissingOnServer(@Param("guildId") long guildId, @Param("serverId") long serverId);
}
