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
    @Query(value = """
select id, departure_date as departureDate, train_number as trainNumber, timestamp, st_x(location) as x, st_y(location) as y, speed, accuracy, station_short_code as stationShortCode, commercial_track as commercialTrack, ut as unknownTrack from (
    select tl.id, tl.departure_date, tl.train_number, timestamp, location, speed, accuracy, tr.station_short_code, commercial_track, tr.unknown_track ut, rank()
    over (partition by id order by scheduled_time asc, type desc) as r
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
