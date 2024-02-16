package fi.livi.rata.avoindata.common.dao.gtfs;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;

import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrainLocation;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GTFSTrainRepository extends CustomGeneralRepository<GTFSTrain, TrainId> {
    @Query("select train from GTFSTrain train" +
            " where train.version > ?1" +
            // category must be Commuter or Long-distance and traintype must not be V, HV or MV
            " and train.trainCategoryId in (1, 2) and train.trainTypeId not in (81, 52, 53)" +
            " and train.id.departureDate in (current_date, (current_date - 1))")
    List<GTFSTrain> findByVersionGreaterThan(final long version);

    @Query(value = "select id, departure_date as departureDate, train_number as trainNumber, timestamp, location, speed, accuracy, station_short_code as stationShortCode, commercial_track as commercialTrack from (" +
            " select tl.id, tl.departure_date, tl.train_number, timestamp, location, speed, accuracy, tr.station_short_code, commercial_track, rank()" +
            " over (partition by id order by scheduled_time desc, type desc) as r" +
            " from train_location tl" +
            " left join time_table_row tr " +
            "   on tl.departure_date = tr.departure_date" +
            "   and tl.train_number = tr.train_number" +
            "   and tr.commercial_stop is true" +
            "   and tr.actual_time is not null" +
            " where id in (:ids)) data" +
            " where r = 1", nativeQuery = true)
    List<GTFSTrainLocation> getTrainLocations(@Param("ids") final List<Long> locationIds);
}
