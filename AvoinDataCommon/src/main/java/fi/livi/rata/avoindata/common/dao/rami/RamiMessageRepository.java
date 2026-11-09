package fi.livi.rata.avoindata.common.dao.rami;

import fi.livi.rata.avoindata.common.domain.rami.RamiMessage;
import fi.livi.rata.avoindata.common.domain.rami.RamiMessageKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface RamiMessageRepository extends JpaRepository<RamiMessage, RamiMessageKey> {

    /**
     * Latest version of each message that either is still valid, or was deleted before it expired
     * and not before {@code windowStart} (the start of the window in which a deleted message is still published).
     * A message is found even if its validity has not started yet, so it is published as soon as it is created.
     * The {@code endValidity > windowStart} condition is implied by the others; it is there so that the index can be
     * used.
     */
    @Query("""
            select distinct m from RamiMessage m
            left join fetch m.videos
            left join fetch m.audios
            left join fetch m.stations
            where m.version = (select max(l.version) from RamiMessage l where l.id = m.id)
            and m.endValidity > :windowStart
            and ((m.deleted is null and m.endValidity > :now)
                 or (m.deleted is not null and m.deleted < m.endValidity and m.deleted > :windowStart))
            """)
    List<RamiMessage> findLatestPublishable(@Param("now") Instant now, @Param("windowStart") Instant windowStart);

    @Query("select m.id as id, min(m.createdSource) as created from RamiMessage m where m.id in :ids group by m.id")
    List<RamiFirstCreated> findFirstCreated(@Param("ids") Collection<String> ids);
}
