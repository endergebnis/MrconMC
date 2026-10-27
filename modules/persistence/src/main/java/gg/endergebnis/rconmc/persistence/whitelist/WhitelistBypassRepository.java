package gg.endergebnis.rconmc.persistence.whitelist;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Alle Abfragen sind nach {@code guildId} gefiltert: Freigaben gelten nur in der Guild, in der sie vergeben wurden. */
public interface WhitelistBypassRepository extends JpaRepository<WhitelistBypass, Long> {

    boolean existsByGuildIdAndDiscordId(long guildId, String discordId);

    List<WhitelistBypass> findByGuildIdOrderByIdAsc(long guildId);

    long countByGuildId(long guildId);

    /** @return die Anzahl geloeschter Zeilen (0 = es gab keine Freigabe) */
    @Modifying
    @Transactional
    @Query("delete from WhitelistBypass b where b.guildId = :guildId and b.discordId = :discordId")
    int deleteByGuildAndDiscord(@Param("guildId") long guildId, @Param("discordId") String discordId);
}
