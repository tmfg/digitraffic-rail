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
    /// Design intent of the `actual_time is null` filter: it was written purely so GTFS-Realtime gets the
    /// correct "next station" short code for a `VehiclePosition`/`TripUpdate` - the row is joined to the
    /// location only for that purpose. It was not designed with SIRI-VM's `VehicleAtStop` in mind, but the row
    /// selection happens to already encode it as a side effect (see below), since "the next relevant station,
    /// whether approaching or currently dwelling at it" is exactly what both consumers need.
    ///
    /// vehicle_at_stop: `time_table_row.type` is stored by JPA ordinal (ARRIVAL=0, DEPARTURE=1). Since the
    /// resolved row is always the *earliest not-yet-happened* commercial row, its type tells us whether the
    /// train is currently approaching a stop (resolved row is that stop's ARRIVAL - it hasn't happened yet, so
    /// not at the stop) or already dwelling there / not yet departed the origin (that stop's ARRIVAL is either
    /// already actual or doesn't exist for the origin, so the earliest not-yet-happened row is its DEPARTURE) -
    /// see SIRI-VM MonitoredCallStructure.VehicleAtStop in docs/SIRI-VM-IMPLEMENTATION-PLAN.md for the derivation.
    ///
    /// Worked example (station TPE with ARRIVAL 10:00 / DEPARTURE 10:02):
    /// - Approaching TPE: both TPE rows still have actual_time = null (train hasn't arrived, let alone left) ->
    ///   both compete in the window; ARRIVAL wins on `scheduled_time asc` (10:00 < 10:02) -> resolved row is
    ///   ARRIVAL (type=0) -> vehicle_at_stop = false.
    /// - Dwelling at TPE: ARRIVAL's actual_time is now set (it happened), so it drops out of the filter
    ///   entirely; only DEPARTURE (actual_time still null) remains eligible for TPE -> resolved row is DEPARTURE
    ///   (type=1) -> vehicle_at_stop = true. StopPointRef is unchanged (same station on both rows).
    /// - Not yet departed the origin station (which has no ARRIVAL row at all): the same DEPARTURE-only
    ///   situation applies from the very start -> vehicle_at_stop = true there too.
    @Query(value = """
select id, departure_date as departureDate, train_number as trainNumber, timestamp, st_x(location) as x, st_y(location) as y, speed, accuracy, station_short_code as stationShortCode, commercial_track as commercialTrack, ut as unknownTrack, delay_seconds as delaySeconds, vehicle_at_stop as vehicleAtStop from (
    select tl.id, tl.departure_date, tl.train_number, timestamp, location, speed, accuracy, tr.station_short_code, commercial_track, tr.unknown_track ut,
    timestampdiff(SECOND, tr.scheduled_time, tr.live_estimate_time) as delay_seconds,
    case when tr.type is null then null when tr.type = 1 then true else false end as vehicle_at_stop, rank()
    over (partition by id order by scheduled_time, type) as r
    from train_location tl
    left join time_table_row tr
        on tl.departure_date = tr.departure_date
        and tl.train_number = tr.train_number
        and tr.commercial_stop is true
        and tr.cancelled is false
        and tr.actual_time is null
        and tr.live_estimate_time > CURRENT_TIMESTAMP()
    where id in (:ids)) data
where r = 1""", nativeQuery = true)
    List<GTFSTrainLocation> getTrainLocations(@Param("ids") final List<Long> locationIds);
}
