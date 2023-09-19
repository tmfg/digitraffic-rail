package fi.livi.rata.avoindata.common.dao.trackwork;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.hibernate.annotations.NamedNativeQuery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.dao.RumaNotificationIdAndVersion;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotificationState;

@Repository
public interface TrackWorkNotificationRepository extends CustomGeneralRepository<TrackWorkNotification, TrackWorkNotification.TrackWorkNotificationId> {

    @Query("SELECT t.id.id AS id, t.id.version AS version FROM TrackWorkNotification t WHERE t.id.id IN (:ids) ORDER by t.id.id, t.id.version ASC")
    List<RumaNotificationIdAndVersion> findIdsAndVersions(@Param("ids") final Set<String> ids);

    @Query("SELECT t FROM TrackWorkNotification t WHERE t.id.id = :id ORDER by t.id.id, t.id.version ASC")
    List<TrackWorkNotification> findByTwnId(@Param("id") final String id);

    @Query(value = "SELECT * FROM track_work_notification t WHERE t.id = :id ORDER by t.version DESC LIMIT 1", nativeQuery = true)
    Optional<TrackWorkNotification> findByTwnIdLatest(@Param("id") final String id);

    @Query("SELECT t FROM TrackWorkNotification t WHERE t.id.id = :id AND t.id.version = :version")
    Optional<TrackWorkNotification> findByTwnIdAndVersion(@Param("id") final String id, @Param("version") final long version);

    @Query("SELECT t.id.id AS id, MAX(t.id.version) AS version, MAX(t.modified) AS modified FROM TrackWorkNotification t WHERE t.modified BETWEEN :start AND :end GROUP BY t.id.id ORDER BY modified ASC, t.id.id ASC")
    List<RumaNotificationIdAndVersion> findByModifiedBetween(@Param("start") final ZonedDateTime start, @Param("end") final ZonedDateTime end, final Pageable pageable);

    @Query(value = "SELECT t.id, MAX(t.version) " +
            "FROM track_work_notification t " +
            "WHERE t.modified BETWEEN :start AND :end GROUP BY t.id", nativeQuery = true)
    List<Object[]> findLatestBetween(
            @Param("start") final ZonedDateTime start,
            @Param("end") final ZonedDateTime end);

    @Query("SELECT t FROM TrackWorkNotification t " +
            "LEFT JOIN FETCH t.trackWorkParts twp " +
            "LEFT JOIN FETCH twp.locations rl " +
            "LEFT JOIN FETCH rl.identifierRanges ir " +
            "LEFT JOIN FETCH ir.elementRanges " +
            "WHERE t.state IN (:states) AND (t.modified BETWEEN :start and :end) " +
            "AND t.id IN (:ids) " +
            "ORDER BY t.modified ASC, t.id.id ASC")
    List<TrackWorkNotification> findByStateAndId(
            @Param("states") final Set<TrackWorkNotificationState> states,
            @Param("start") final ZonedDateTime start,
            @Param("end") final ZonedDateTime end,
            @Param("ids") final List<TrackWorkNotification.TrackWorkNotificationId> ids
    );
}
