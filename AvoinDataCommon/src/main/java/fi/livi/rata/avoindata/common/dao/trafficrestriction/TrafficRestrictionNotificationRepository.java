package fi.livi.rata.avoindata.common.dao.trafficrestriction;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.dao.RumaNotificationIdAndVersion;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotification;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotificationState;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface TrafficRestrictionNotificationRepository extends CustomGeneralRepository<TrafficRestrictionNotification, TrafficRestrictionNotification.TrafficRestrictionNotificationId> {

    @Query("SELECT t.id.id AS id, t.id.version AS version FROM TrafficRestrictionNotification t WHERE t.id.id IN (:ids) ORDER by id, version ASC")
    List<RumaNotificationIdAndVersion> findIdsAndVersions(@Param("ids") Set<String> ids);

    @Query("SELECT t FROM TrafficRestrictionNotification t WHERE t.id.id = :id AND t.limitation <> fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionType.OTHER ORDER by id, version ASC")
    List<TrafficRestrictionNotification> findByTrnId(@Param("id") String id);

    @Query("SELECT t FROM TrafficRestrictionNotification t WHERE t.id.id = :id AND t.id.version = :version AND t.limitation <> fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionType.OTHER")
    Optional<TrafficRestrictionNotification> findByTrnIdAndVersion(@Param("id") String id, @Param("version") long version);

    @Query(value = "SELECT * FROM traffic_restriction_notification t WHERE t.id = :id ORDER by version DESC LIMIT 1", nativeQuery = true)
    Optional<TrafficRestrictionNotification> findByTrnIdLatest(@Param("id") String id);

    @Query("SELECT t.id.id AS id, MAX(t.id.version) AS version, MAX(t.modified) AS modified FROM TrafficRestrictionNotification t WHERE t.modified BETWEEN :start AND :end AND t.limitation <> fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionType.OTHER GROUP BY t.id.id ORDER BY modified ASC, id ASC")
    List<RumaNotificationIdAndVersion> findByModifiedBetween(@Param("start") ZonedDateTime start, @Param("end") ZonedDateTime end, Pageable pageable);

    @Query("SELECT t FROM TrafficRestrictionNotification t WHERE t.state IN (:states) AND t.limitation <> fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionType.OTHER AND (t.id.id, t.id.version) IN " +
             "(SELECT t2.id.id, MAX(t2.id.version) FROM TrafficRestrictionNotification t2 WHERE t2.modified BETWEEN :start AND :end GROUP BY t2.id.id) " +
           "ORDER BY t.modified ASC, t.id.id ASC")
    List<TrafficRestrictionNotification> findByState(
            @Param("states") Set<TrafficRestrictionNotificationState> states,
            @Param("start") ZonedDateTime start,
            @Param("end") ZonedDateTime end,
            Pageable pageable);
}
