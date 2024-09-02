package fi.livi.rata.avoindata.common.dao.gtfs;

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
            " where train.version > ?1" +
            // category must be Commuter or Long-distance and traintype must not be V, HV or MV
            " and train.trainCategoryId in (1, 2) and train.trainTypeId not in (81, 52, 53)" +
            " and train.id.departureDate in (current_date, (current_date - 1 day))")
    List<GTFSTrain> findByVersionGreaterThan(final long version);

    /// generate (next) stop_id from the first commercial, not cancelled row
    /// that does not have actual_time yet and the estimate is in the future
    @Query(value = """
            select id, departure_date as departureDate, train_number as trainNumber, timestamp, st_x(location) as x, st_y(location) as y, speed, accuracy, station_short_code as stationShortCode, commercial_track as commercialTrack from (
                select tl.id, tl.departure_date, tl.train_number, timestamp, location, speed, accuracy, tr.station_short_code, commercial_track, rank()
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
            where r = 1""",
           nativeQuery = true)
    List<GTFSTrainLocation> getTrainLocations(
            @Param("ids")
            final List<Long> locationIds);
}
