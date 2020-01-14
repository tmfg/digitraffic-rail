package fi.livi.rata.avoindata.common.dao.trackwork;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface TrackWorkNotificationRepository extends CustomGeneralRepository<TrackWorkNotification, TrackWorkNotification.TrackWorkNotificationId> {

    @Query("SELECT t.id.id AS id, t.id.version AS version FROM TrackWorkNotification t WHERE t.id.id IN (:ids) ORDER by id, version ASC")
    List<TrackWorkNotificationIdAndVersion> findIdsAndVersions(@Param("ids") Set<Integer> ids);

    @Query("SELECT t FROM TrackWorkNotification t WHERE t.id.id = :id ORDER by id, version ASC")
    List<TrackWorkNotification> findByTwnId(@Param("id") int id);

    @Query("SELECT t FROM TrackWorkNotification t WHERE t.id.id = :id AND t.id.version = :version")
    Optional<TrackWorkNotification> findByTwnIdAndVersion(@Param("id") int id, @Param("version") int version);

    @Query("SELECT t.id.id AS id, MAX(t.id.version) AS version FROM TrackWorkNotification t WHERE t.modified BETWEEN :start AND :end GROUP BY t.id.id ORDER BY id ASC")
    List<TrackWorkNotificationIdAndVersion> findByModifiedBetween(@Param("start") ZonedDateTime start, @Param("end") ZonedDateTime end, Pageable pageable);
}
