package gg.endergebnis.rconmc.persistence.whitelist;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface WhitelistEntryRepository extends JpaRepository<WhitelistEntry, Long> {

    boolean existsByPlayerIdAndServerId(long playerId, long serverId);

    @Query("""
            select s.name from MinecraftServer s, WhitelistEntry e
            where e.serverId = s.id and e.playerId = :playerId
            order by s.name
            """)
    List<String> serverNamesOfPlayer(@Param("playerId") long playerId);

    @Modifying
    @Transactional
    @Query("delete from WhitelistEntry e where e.playerId = :playerId")
    void deleteByPlayer(@Param("playerId") long playerId);

    @Modifying
    @Transactional
    @Query("delete from WhitelistEntry e where e.serverId = :serverId")
    void deleteByServer(@Param("serverId") long serverId);

    @Modifying
    @Transactional
    @Query("delete from WhitelistEntry e where e.playerId = :playerId and e.serverId = :serverId")
    void deleteByPlayerAndServer(@Param("playerId") long playerId, @Param("serverId") long serverId);
}
