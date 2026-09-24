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
    /// Sentinel `version` for {@link #findBySourceVersionAndIdIn}, for callers that already know the exact
    /// `TrainId`s they want (e.g. from an already-fetched location/journey) and so have no freshness floor to
    /// enforce - `sourceVersion` is always set from the train's own payload version ({@code
    /// TrainInitializerService}: {@code t.sourceVersion = t.version}), a positive, monotonically increasing
    /// number for every sourced train, so `> ANY_SOURCE_VERSION` (0) matches any of them without filtering.
    long ANY_SOURCE_VERSION = 0L;

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
    /// stop was resolved - **including** the terminus case (see below), which this query deliberately leaves
    /// null rather than resolving in SQL.
    ///
    /// unknownDelay: `time_table_row.unknown_delay` is set by the source system (RAMI) when it cannot
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
    /// tell whether the train is currently dwelling: this query's `live_estimate_time > CURRENT_TIMESTAMP()`
    /// filter can exclude an ARRIVAL row whose *estimate* is merely stale (not yet refreshed), even though the
    /// train has not actually reached it (`actual_time` still null) - in that case the next commercial row, the
    /// DEPARTURE, gets selected instead, and `type = 1` alone would wrongly report `vehicle_at_stop = true`
    /// despite the train never having arrived. So a selected DEPARTURE only means "at stop" when its paired
    /// ARRIVAL (the latest ARRIVAL at the same station scheduled at or before it) has actually happened
    /// (`actual_time` is not null) - or there is no such ARRIVAL at all, i.e. the station is the train's
    /// origin, which has no ARRIVAL row and is "at stop" (not yet departed) from the very start. Computed with
    /// a window function (`row_number`/`max` over each station's own rows, ordered by `scheduled_time`) rather
    /// than a per-row correlated subquery, so every candidate row's paired-ARRIVAL check is a single pass
    /// instead of one extra lookup per row.
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
    ///   null) so this query's filter drops it, letting TPE DEPARTURE (with a future estimate) be selected
    ///   instead - but since the paired ARRIVAL's actual_time is still null, vehicle_at_stop = false, not true.
    ///
    /// Terminus fallback: a terminus has only an ARRIVAL row (no DEPARTURE), so once that ARRIVAL's
    /// `actual_time` is set (train has arrived), this query has nothing left to match for that train and
    /// returns a `null` `stationShortCode` (and every other stop field) for it - deliberately: unlike the
    /// query's own "next stop" lookup (shared, unchanged, with GTFS-Realtime), reporting an *already-arrived*
    /// terminus is only ever needed by SIRI-VM (so its `VehicleAtStop`/`MonitoredCall` isn't silently dropped
    /// while the train dwells there), and is resolved by SIRI-VM in Java instead, from the train's full row
    /// list - see `CommercialStopVisits#resolveTerminusFallback`, wired in `SiriVmGenerationService`. Keeping
    /// this out of the query keeps it - and GTFS-Realtime, which never needed a terminus fallback in the first
    /// place - to the single, simple "next stop" lookup below.
    @Query(value = """
select tl.id as id, tl.departure_date as departureDate, tl.train_number as trainNumber, tl.timestamp as timestamp,
    st_x(tl.location) as x, st_y(tl.location) as y, tl.speed as speed, tl.accuracy as accuracy,
    next.station_short_code as stationShortCode,
    next.commercial_track as commercialTrack,
    next.unknown_track as unknownTrack,
    next.unknown_delay as unknownDelay,
    next.delay_seconds as delaySeconds,
    next.vehicle_at_stop as vehicleAtStopValue
from train_location tl
-- next: the trains current/upcoming commercial stop - the earliest commercial row that has not happened yet
-- (actual_time is null) and whose live estimate is still in the future (see the class-level comment above for
-- why this filter exists and how it derives vehicle_at_stop).
-- lateral allows the subquery to reference the outer querys tl.train_number and tl.departure_date, so it can
-- select the next stop for that specific train with limit = 1.
left join lateral (
    select station_short_code, commercial_track, unknown_track, unknown_delay, delay_seconds, vehicle_at_stop
    from (
        select tr.station_short_code, tr.commercial_track, tr.unknown_track, tr.unknown_delay,
            tr.type, tr.actual_time, tr.live_estimate_time, tr.scheduled_time,
            timestampdiff(SECOND, tr.scheduled_time, tr.live_estimate_time) as delay_seconds,
            (tr.type = 1 -- DEPARTURE
             and (
                -- No earlier row at all for this station -> origin, no paired ARRIVAL exists -> at stop from
                -- the very start. The "type" tiebreak (ARRIVAL before DEPARTURE) breaks zero-dwell ties (same
                -- stations ARRIVAL/DEPARTURE sharing one scheduled_time) - without it a DEPARTURE could rank
                -- first and wrongly report at-stop before the train has actually arrived. Defensive: no such
                -- tie has actually been observed in production data, but nothing rules it out either.
                row_number() over w = 1
                -- Otherwise: at stop only if the *nearest* preceding ARRIVAL has actually happened - not just
                -- *any* earlier one (a completed first visit must not "leak" into a still-pending repeat
                -- visit). Compare two window-computed keys: the nearest preceding ARRIVALs scheduled_time vs.
                -- the nearest preceding *happened* ARRIVALs scheduled_time (actual_time set). They match only
                -- when the closest ARRIVAL is the one that happened. A cancelled ARRIVAL is simply absent from
                -- both keys, so this naturally skips past it to find the real one - unlike lag(), which only
                -- looks one row back by position and could land on an unrelated row instead.
                -- The explicit "is not null" guard matters because SQLs `=` between two NULLs is NULL, not
                -- false - without it, "no ARRIVAL has happened yet" would make the whole expression NULL
                -- instead of false.
                or (
                    max(case when tr.type = 0 and tr.actual_time is not null then tr.scheduled_time end) over w
                        is not null
                    and max(case when tr.type = 0 and tr.actual_time is not null then tr.scheduled_time end) over w
                        = max(case when tr.type = 0 then tr.scheduled_time end) over w
                )
             )
            ) as vehicle_at_stop
        from time_table_row tr
        where tr.departure_date = tl.departure_date
            and tr.train_number = tl.train_number
            and tr.commercial_stop is true
            and tr.cancelled is false
        window w as (
            -- Shared by row_number() and both max() calls above: row_number() ignores frames entirely (only
            -- partition/order matter to it), so this frame only affects max() - narrowing it to rows strictly
            -- before this one, rather than relying on the default that would also include the current row.
            partition by tr.station_short_code order by tr.scheduled_time, tr.type
            rows between unbounded preceding and 1 preceding
        )
    ) all_commercial_rows
    where actual_time is null
        and live_estimate_time > CURRENT_TIMESTAMP()
    -- type desc breaks a same-instant tie in favor of DEPARTURE: two rows tie only when the current
    -- stations DEPARTURE and the next stations ARRIVAL share the same scheduled_time (zero scheduled
    -- transit time), and DEPARTURE must win - it belongs to the station the train has not yet left,
    -- whereas ARRIVAL-first would prematurely advance the reported stop to the next station before departure.
    order by scheduled_time, type desc
    limit 1
) next on true
where tl.id in (:ids)""", nativeQuery = true)
    List<GTFSTrainLocation> getTrainLocations(@Param("ids") final List<Long> locationIds);
}
