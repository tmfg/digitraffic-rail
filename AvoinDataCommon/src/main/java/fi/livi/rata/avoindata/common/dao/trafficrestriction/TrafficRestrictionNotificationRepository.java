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

    @Query("SELECT t.id.id AS id, t.id.version AS version FROM TrafficRestrictionNotification t WHERE t.id.id IN (:ids) ORDER by t.id.id, t.id.version ASC")
    List<RumaNotificationIdAndVersion> findIdsAndVersions(@Param("ids") final Set<String> ids);

    @Query("SELECT t FROM TrafficRestrictionNotification t WHERE t.id.id = :id AND t.limitation <> fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionType.OTHER ORDER by t.id.id, t.id.version ASC")
    List<TrafficRestrictionNotification> findByTrnId(@Param("id") final String id);

    @Query("SELECT t FROM TrafficRestrictionNotification t WHERE t.id.id = :id AND t.id.version = :version AND t.limitation <> fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionType.OTHER")
    Optional<TrafficRestrictionNotification> findByTrnIdAndVersion(@Param("id") final String id, @Param("version") final long version);

    @Query(value = "SELECT * FROM traffic_restriction_notification t WHERE t.id = :id ORDER by t.version DESC LIMIT 1", nativeQuery = true)
    Optional<TrafficRestrictionNotification> findByTrnIdLatest(@Param("id") final String id);

    @Query("SELECT t.id.id AS id, MAX(t.id.version) AS version, MAX(t.modified) AS modified FROM TrafficRestrictionNotification t WHERE t.modified BETWEEN :start AND :end AND t.limitation <> fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionType.OTHER GROUP BY t.id.id ORDER BY modified ASC, t.id.id ASC")
    List<RumaNotificationIdAndVersion> findByModifiedBetween(@Param("start") final ZonedDateTime start, @Param("end") final ZonedDateTime end, final Pageable pageable);
    @Query(value = "select id, version, axle_weight_max, created, end_date, finished, limitation, location_map, location_schema, modified, organization, start_date, state, twn_id " +
            "from traffic_restriction_notification t1_0 " +
            "where t1_0.state in(:states) " +
            "and t1_0.limitation != 7 and (t1_0.id, t1_0.version) in (" +
                "select t2_0.id, max(t2_0.version) " +
                "from traffic_restriction_notification t2_0 " +
                "where t2_0.modified between :start and :end " +
                "group by t2_0.id) " +
            "order by t1_0.modified asc, t1_0.id asc " +
            "#pageable", nativeQuery = true)
    List<TrafficRestrictionNotification> findByState(
            @Param("states") final Set<Integer> states,
            @Param("start") final ZonedDateTime start,
            @Param("end") final ZonedDateTime end,
            final Pageable pageable);
}
