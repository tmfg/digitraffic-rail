package fi.livi.rata.avoindata.common.dao.gtfs;

import java.util.Collection;
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
    @Query("select train from GTFSTrain train" +
            " where train.sourceVersion > ?1" +
            // category must be Commuter or Long-distance and traintype must not be V, HV or MV
            " and train.trainCategoryId in (1, 2) and train.trainTypeId not in (81, 52, 53)" +
            " and train.id.departureDate in (current_date, (current_date - 1 day))")
    List<GTFSTrain> findBySourceVersionGreaterThan(final long version);

    /// Live trains for the exact (trainNumber, departureDate) set the published NeTEx refers to. SIRI-ET drives
    /// the fetch from the persisted journey refs rather than re-applying the GTFS passenger filter: the
    /// published set is already passenger-filtered and winning-schedule resolved by NeTEx, so binding it here
    /// makes "only emit published journeys" structural. The sourceVersion guard drops not-yet-sourced rows.
    @Query("select train from GTFSTrain train" +
            " where train.sourceVersion > :version" +
            " and train.id in :ids")
    List<GTFSTrain> findBySourceVersionAndIdIn(@Param("version") final long version,
            @Param("ids") final Collection<TrainId> ids);

    /// generate (next) stop_id from the first commercial, not cancelled row
    /// that does not have actual_time yet and the estimate is in the future
    /// delay_seconds is the live estimate's real-time delay against that same upcoming stop's scheduled time
    /// (SIRI-VM/Nordic profile carries both position and delay - see Handbook N801 5.1); null when no upcoming
    /// stop was resolved.
    ///
    /// unknownDelay: `time_table_row.unknown_delay` is set by the source system (LIIKE) when it cannot
    /// estimate how long a train will actually have to wait - i.e. `delay_seconds`/`live_estimate_time` exist
    /// but are known to be unreliable. The Nordic SIRI-VM profile has no dedicated "estimate is unreliable"
    /// field, so this is surfaced via `InCongestion` (see `VmJourneyConverter`/`VmJourneyMarshaller`) instead:
    /// its profile wording ("affected by ... other circumstances which may lead to further delays") fits an
    /// unknown-wait situation reasonably well, and no better-fitting field exists. `Delay` itself is still
    /// always computed/emitted as normal (it is mandatory, 1:1, in the profile) - `unknownDelay` only adds this
    /// extra "don't fully trust it" signal alongside it, it never suppresses `Delay`.
    ///
    /// Design intent of the `actual_time is null` filter: it was written purely so GTFS-Realtime gets the
    /// correct "next station" short code for a `VehiclePosition`/`TripUpdate` - the row is joined to the
    /// location only for that purpose. It was not designed with SIRI-VM's `VehicleAtStop` in mind, but the row
    /// selection happens to already encode it as a side effect (see below), since "the next relevant station,
    /// whether approaching or currently dwelling at it" is exactly what both consumers need.
    ///
    /// vehicle_at_stop: `time_table_row.type` is stored by JPA ordinal (ARRIVAL=0, DEPARTURE=1). Since the
    /// resolved row is always the *earliest not-yet-happened* commercial row, its type alone is not enough to
    /// tell whether the train is currently dwelling: `next`'s `live_estimate_time > CURRENT_TIMESTAMP()` filter
    /// can exclude an ARRIVAL row whose *estimate* is merely stale (not yet refreshed), even though the train
    /// has not actually reached it (`actual_time` still null) - in that case the next commercial row, the
    /// DEPARTURE, gets selected instead, and `type = 1` alone would wrongly report `vehicle_at_stop = true`
    /// despite the train never having arrived. So a selected DEPARTURE only means "at stop" when its paired
    /// ARRIVAL (the latest ARRIVAL at the same station scheduled at or before it) has actually happened
    /// (`actual_time` is not null) - or there is no such ARRIVAL at all, i.e. the station is the train's
    /// origin, which has no ARRIVAL row and is "at stop" (not yet departed) from the very start.
    ///
    /// Worked example (station TPE with ARRIVAL 10:00 / DEPARTURE 10:02):
    /// - Approaching TPE: both TPE rows still have actual_time = null (train hasn't arrived, let alone left) ->
    ///   both compete in the window; ARRIVAL wins on `scheduled_time asc` (10:00 < 10:02) -> resolved row is
    ///   ARRIVAL (type=0) -> vehicle_at_stop = false.
    /// - Dwelling at TPE: ARRIVAL's actual_time is now set (it happened), so it drops out of the filter
    ///   entirely; only DEPARTURE (actual_time still null) remains eligible for TPE -> resolved row is DEPARTURE
    ///   (type=1), its paired ARRIVAL is actually done -> vehicle_at_stop = true. StopPointRef is unchanged
    ///   (same station on both rows).
    /// - Not yet departed the origin station (which has no ARRIVAL row at all): the same DEPARTURE-only
    ///   situation applies from the very start -> vehicle_at_stop = true there too (no paired ARRIVAL exists).
    /// - Stale estimate, not actually arrived: TPE ARRIVAL's estimate is stuck in the past (actual_time still
    ///   null) so `next`'s filter drops it, letting TPE DEPARTURE (with a future estimate) be selected instead -
    ///   but since the paired ARRIVAL's actual_time is still null, vehicle_at_stop = false, not true.
    ///
    /// Terminus fallback (`term` lateral join): a terminus has only an ARRIVAL row (no DEPARTURE), so once
    /// that ARRIVAL's `actual_time` is set (train has arrived), the primary `next` lateral join above has
    /// nothing left to match for that train - without this fallback the LEFT JOIN would return no stop at all,
    /// silently dropping the terminus from the location instead of reporting the train as dwelling there.
    /// `term` matches the train's actually-arrived terminus directly (the commercial ARRIVAL row with no later
    /// commercial row for the same train) and reports it with `vehicle_at_stop = true`; `coalesce` prefers
    /// `next` whenever it has a match, so `term` only ever supplies a row when the train has nothing left to
    /// approach.
    ///
    /// `term`'s own lateral is unconditionally materialized once per `train_location` row: the `ON` clause
    /// below is a join predicate, not a gate on the derived table's evaluation, so MySQL computes `term` for
    /// every row regardless of whether `next` already matched (verified with `EXPLAIN ANALYZE`, MySQL 8.0.46:
    /// `Materialize ... loops=<row count>` is unconditional). Because of that, `term`'s own query must stay
    /// cheap on its own merits - it deliberately does *not* filter by type/actual_time before sorting (that
    /// would force a correlated check per candidate row instead of once per train_location row); instead it
    /// picks the chronologically last commercial, non-cancelled row directly (same shape/cost as `next`'s own
    /// lookup) and only then checks, via the `ON` clause, whether that single already-materialized row happens
    /// to be an ARRIVAL with `actual_time` set.
    @Query(value = """
select tl.id as id, tl.departure_date as departureDate, tl.train_number as trainNumber, tl.timestamp as timestamp,
    st_x(tl.location) as x, st_y(tl.location) as y, tl.speed as speed, tl.accuracy as accuracy,
    -- next has a row whenever the train still has an upcoming/current stop to report; term only ever supplies
    -- one when next has nothing left (see its own comment below) - coalesce always prefers next.
    coalesce(next.station_short_code, term.station_short_code) as stationShortCode,
    coalesce(next.commercial_track, term.commercial_track) as commercialTrack,
    coalesce(next.unknown_track, term.unknown_track) as unknownTrack,
    coalesce(next.unknown_delay, term.unknown_delay) as unknownDelay,
    coalesce(next.delay_seconds, term.delay_seconds) as delaySeconds,
    coalesce(next.vehicle_at_stop, term.vehicle_at_stop) as vehicleAtStopValue
from train_location tl
-- next: the trains current/upcoming commercial stop - the earliest commercial row that has not happened yet
-- (actual_time is null) and whose live estimate is still in the future (see the class-level comment above for
-- why this filter exists and how it derives vehicle_at_stop).
left join lateral (
    select tr.station_short_code, tr.commercial_track, tr.unknown_track, tr.unknown_delay,
        timestampdiff(SECOND, tr.scheduled_time, tr.live_estimate_time) as delay_seconds,
        (tr.type = 1 -- DEPARTURE
         and coalesce(
            -- Only a selected DEPARTURE with its paired ARRIVAL actually completed (or no ARRIVAL at all, i.e.
            -- the origin) counts as at-stop - see worked examples above.
            (select arr.actual_time is not null
             from time_table_row arr
             where arr.departure_date = tr.departure_date
                 and arr.train_number = tr.train_number
                 and arr.station_short_code = tr.station_short_code
                 and arr.commercial_stop is true
                 and arr.cancelled is false
                 and arr.type = 0 -- ARRIVAL
                 and arr.scheduled_time <= tr.scheduled_time
             order by arr.scheduled_time desc
             limit 1),
            true)
        ) as vehicle_at_stop
    from time_table_row tr
    where tr.departure_date = tl.departure_date
        and tr.train_number = tl.train_number
        and tr.commercial_stop is true
        and tr.cancelled is false
        and tr.actual_time is null
        and tr.live_estimate_time > CURRENT_TIMESTAMP()
    -- type desc breaks a same-instant tie in favor of DEPARTURE: two rows tie only when the current
    -- stations DEPARTURE and the next stations ARRIVAL share the same scheduled_time (zero scheduled
    -- transit time), and DEPARTURE must win - it belongs to the station the train has not yet left,
    -- whereas ARRIVAL-first would prematurely advance the reported stop to the next station before departure.
    order by tr.scheduled_time, tr.type desc
    limit 1
) next on true
-- term: terminus fallback (see class-level comment above for why its own lateral is unconditionally
-- materialized, and why it is written to stay cheap despite that).
left join lateral (
    select tr.station_short_code, tr.commercial_track, tr.unknown_track, tr.unknown_delay,
        timestampdiff(SECOND, tr.scheduled_time, tr.actual_time) as delay_seconds,
        1 as vehicle_at_stop,
        tr.type as row_type,
        tr.actual_time as row_actual_time
    from time_table_row tr
    where tr.departure_date = tl.departure_date
        and tr.train_number = tl.train_number
        and tr.commercial_stop is true
        and tr.cancelled is false
    -- the last commercial, non-cancelled row overall - type asc breaks a same-instant ARRIVAL/DEPARTURE tie
    -- in favor of ARRIVAL (type=0), matching what the ON clause below requires.
    order by tr.scheduled_time desc, tr.type asc
    limit 1
-- only an actually-arrived (actual_time set) ARRIVAL (type=0) row qualifies as the terminus fallback, and only
-- when next found nothing.
) term on next.station_short_code is null
    and term.row_type = 0 -- ARRIVAL
    and term.row_actual_time is not null
where tl.id in (:ids)""", nativeQuery = true)
    List<GTFSTrainLocation> getTrainLocations(@Param("ids") final List<Long> locationIds);
}
