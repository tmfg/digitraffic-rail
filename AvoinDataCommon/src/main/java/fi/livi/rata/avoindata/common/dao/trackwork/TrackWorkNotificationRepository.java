package fi.livi.rata.avoindata.common.dao.trackwork;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface TrackWorkNotificationRepository extends CustomGeneralRepository<TrackWorkNotification, TrackWorkNotification.TrackWorkNotificationId> {

    @Query("SELECT t.id.id AS id, t.id.version AS version FROM TrackWorkNotification t WHERE t.id.id IN (?1) ORDER by id, version ASC")
    List<TrackWorkNotificationIdAndVersion> findIdsAndVersions(Set<Integer> ids);

    @Query("SELECT t FROM TrackWorkNotification t WHERE t.id.id = ?1 ORDER by id, version ASC")
    List<TrackWorkNotification> findByTwnId(int id);

    @Query("SELECT t FROM TrackWorkNotification t WHERE t.id.id = ?1 AND t.id.version = ?2")
    Optional<TrackWorkNotification> findByTwnIdAndVersion(int id, int version);

    @Query("SELECT t.id.id AS id, MAX(t.id.version) AS version FROM TrackWorkNotification t WHERE t.modified BETWEEN ?1 AND ?2 GROUP BY t.id.id ORDER BY id ASC")
    List<TrackWorkNotificationIdAndVersion> findByModifiedBetween(ZonedDateTime start, ZonedDateTime end);
}
