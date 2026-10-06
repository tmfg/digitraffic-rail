package fi.livi.rata.avoindata.common.dao.gtfs;

import java.util.Collection;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrainLocation;

@Repository
public interface GTFSTrainRepository extends CustomGeneralRepository<GTFSTrain, TrainId> {
    /// Special `version` value for {@link #findBySourceVersionAndIdIn}: since `sourceVersion` is 0/unset only
    /// until a trains real data has been sourced (see TrainInitializerService), passing 0 here accepts any
    /// already-sourced train regardless of how old its data is - the caller already knows the exact TrainIds it
    /// wants and is not trying to filter by freshness.
    long ANY_SOURCE_VERSION = 0L;

    // return trains that have changed since given version, departing today or yesterday
    @Query("select train from GTFSTrain train" +
            " where train.sourceVersion > :version" +
            // category must be Commuter or Long-distance and traintype must not be V, HV or MV
            " and train.trainCategoryId in (1, 2) and train.trainTypeId not in (81, 52, 53)" +
            " and train.id.departureDate in (:today, (:today - 1 day))")
    List<GTFSTrain> findBySourceVersionGreaterThan(final long version, final LocalDate today);

    /// Live trains for the exact (trainNumber, departureDate) set the published NeTEx refers to. SIRI-ET drives
    /// the fetch from the persisted journey refs rather than re-applying the GTFS passenger filter: the
    /// published set is already passenger-filtered and winning-schedule resolved by NeTEx, so binding it here
    /// makes "only emit published journeys" structural. The sourceVersion guard drops not-yet-sourced rows.
    @Query("select train from GTFSTrain train" +
            " where train.sourceVersion > :version" +
            " and train.id in :ids")
    List<GTFSTrain> findBySourceVersionAndIdIn(@Param("version") final long version,
            @Param("ids") final Collection<TrainId> ids);

    /// next: the earliest commercial, not-cancelled row that has not happened yet (actual_time is null) and
    /// whose live estimate is in the future.
    ///
    /// delaySeconds: the live estimates delay against nexts scheduled time (SIRI-VM/Nordic profile carries
    /// both position and delay - Handbook N801 5.1); null when next itself is null (including the terminus
    /// case below).
    ///
    /// unknownDelay: set by RAMI when it cannot estimate the wait reliably. No dedicated SIRI-VM field exists,
    /// so it is surfaced via InCongestion instead (see VmJourneyConverter) - it never suppresses Delay itself.
    ///
    /// vehicleAtStop: nexts type alone does not tell whether the train is dwelling, since a stale-estimate
    /// ARRIVAL can be skipped in favor of its DEPARTURE even though the train has not arrived. So a DEPARTURE
    /// only counts as "at stop" when prev - the nearest ARRIVAL at the same station as next, resolved by its
    /// own independent lateral join - has actually happened (or does not exist at all, i.e. an origin station).
    ///
    /// Terminus fallback: a terminus has no DEPARTURE row, so once its ARRIVAL happens this query has nothing
    /// left to match and returns stationShortCode = null - SIRI-VM resolves that case separately in Java (see
    /// CommercialStopVisits#resolveTerminusFallback / SiriVmGenerationService), keeping this query's own
    /// resolution the single "next stop" lookup GTFS-Realtime already relied on.
    @Query(value = """
        SELECT
            tl.id AS id,
            tl.departure_date AS departureDate,
            tl.train_number AS trainNumber,
            tl.timestamp AS timestamp,
            st_x(tl.location) AS x,
            st_y(tl.location) AS y,
            tl.speed AS speed,
            tl.accuracy AS accuracy,
            next.station_short_code AS stationShortCode,
            next.commercial_track AS commercialTrack,
            next.unknown_track AS unknownTrack,
            next.unknown_delay AS unknownDelay,
            timestampdiff(SECOND, next.scheduled_time, next.live_estimate_time) AS delaySeconds,
            (next.type = 1 AND (prev.found IS NULL OR prev.actual_time IS NOT NULL)) AS vehicleAtStopValue
        FROM train_location tl
        -- next: the trains current/upcoming commercial stop (see the class-level comment above for what this
        -- resolves and how it derives vehicle_at_stop).
        -- lateral allows the subquery to reference the outer querys tl.train_number and tl.departure_date, so
        -- it can select the next stop for that specific train with limit = 1.
        LEFT JOIN LATERAL (
            SELECT
                tr.station_short_code,
                tr.commercial_track,
                tr.unknown_track,
                tr.unknown_delay,
                tr.type,
                tr.scheduled_time,
                tr.live_estimate_time
            FROM time_table_row tr
            WHERE tr.departure_date = tl.departure_date
              AND tr.train_number = tl.train_number
              AND tr.commercial_stop IS TRUE
              AND tr.cancelled IS FALSE
              AND tr.actual_time IS NULL
              AND tr.live_estimate_time > CURRENT_TIMESTAMP()
            -- type desc breaks a same-instant tie in favor of DEPARTURE: two rows tie only when the current
            -- stations DEPARTURE and the next stations ARRIVAL share the same scheduled_time (zero scheduled
            -- transit time), and DEPARTURE must win - it belongs to the station the train has not yet left,
            -- whereas ARRIVAL-first would prematurely advance the reported stop to the next station before
            -- departure.
            ORDER BY tr.scheduled_time, tr.type DESC
            LIMIT 1
        ) next ON TRUE
        -- prev: the nearest non-cancelled ARRIVAL at the same station as next, scheduled at or before nexts own
        -- scheduled_time - used only to derive vehicle_at_stop above. Resolved once per train (correlated
        -- against the single already-resolved next row, not against every candidate row), so this adds one
        -- extra lookup per train, not one per row.
        -- found is 1 when a matching ARRIVAL row exists, and null when none exists - this lets vehicle_at_stop
        -- tell "no such ARRIVAL at all" (found is null, e.g. the origin station) apart from "it exists but
        -- has not happened yet" (found = 1, actual_time still null). actual_time alone cannot tell these
        -- apart, since both cases show up as null actual_time once nothing matches at all.
        LEFT JOIN LATERAL (
            SELECT
                1 AS found,
                arr.actual_time
            FROM time_table_row arr
            WHERE arr.departure_date = tl.departure_date
              AND arr.train_number = tl.train_number
              AND arr.station_short_code = next.station_short_code
              AND arr.commercial_stop IS TRUE
              AND arr.cancelled IS FALSE
              AND arr.type = 0 -- ARRIVAL
              AND arr.scheduled_time <= next.scheduled_time
            ORDER BY arr.scheduled_time DESC
            LIMIT 1
        ) prev ON TRUE
        WHERE tl.id IN (:ids)
        """, nativeQuery = true)
    List<GTFSTrainLocation> getTrainLocations(@Param("ids") final List<Long> locationIds);
}
