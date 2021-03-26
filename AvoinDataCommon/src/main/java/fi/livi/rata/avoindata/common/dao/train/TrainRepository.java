package fi.livi.rata.avoindata.common.dao.train;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.train.LiveTimeTableTrain;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;

@Repository
@Transactional
public interface TrainRepository extends CustomGeneralRepository<Train, TrainId> {

    String BASE_TRAIN_SELECT = "select distinct train from Train train " +
            "   inner join fetch train.timeTableRows timeTableRow " +
            "   left join fetch timeTableRow.causes c " +
            "   left join fetch c.categoryCode categoryCode " +
            "   left join fetch c.detailedCategoryCode detailedCategoryCode " +
            "   left join fetch c.thirdCategoryCode thirdCategoryCode " +
            "   left join fetch timeTableRow.trainReadies tr ";

    String BASE_TRAIN_ORDER = " order by train.id.departureDate, train.id.trainNumber, timeTableRow.scheduledTime, timeTableRow.type";
    String IS_NOT_DELETED = "(train.deleted is null or train.deleted = false)";

    @Query(value = "select * from ((SELECT " +
            "    '1', t.departure_date, t.train_number, t.version" +
            " FROM" +
            "    live_time_table_train t" +
            " WHERE" +
            "   (t.train_stopping = true or t.train_stopping = ?6)" +
            "   AND (?1 is null OR t.station_short_code = ?1)" +
            "   AND t.type = '1'" +
            "   AND (t.train_category_id in ?7)" +
            "   AND t.actual_time IS NOT NULL" +
            "   AND (t.deleted IS NULL OR t.deleted = 0)" +
            " ORDER BY t.actual_time DESC" +
            " LIMIT ?2) UNION ALL (SELECT " +
            "    '2', t.departure_date, t.train_number, t.version" +
            " FROM" +
            "    live_time_table_train t" +
            " WHERE" +
            "   (t.train_stopping = true or t.train_stopping = ?6)" +
            "   AND (?1 is null OR t.station_short_code = ?1)" +
            "   AND t.type = '1'" +
            "   AND (t.train_category_id in ?7)" +
            "   AND t.actual_time IS NULL" +
            "   AND (t.deleted IS NULL OR t.deleted = 0)" +
            " ORDER BY t.predict_time ASC" +
            " LIMIT ?3) UNION ALL (SELECT " +
            "    '3', t.departure_date, t.train_number, t.version" +
            " FROM" +
            "    live_time_table_train t" +
            " WHERE" +
            "   (t.train_stopping = true or t.train_stopping = ?6)" +
            "   AND (?1 is null OR t.station_short_code = ?1)" +
            "   AND t.type = '0'" +
            "   AND (t.train_category_id in ?7)" +
            "   AND t.actual_time IS NOT NULL" +
            "   AND (t.deleted IS NULL OR t.deleted = 0)" +
            " ORDER BY t.actual_time DESC" +
            " LIMIT ?4) UNION ALL (SELECT " +
            "    '4', t.departure_date, t.train_number, t.version" +
            " FROM" +
            "    live_time_table_train t" +
            " WHERE" +
            "   (t.train_stopping = true or t.train_stopping = ?6)" +
            "   AND (?1 is null OR t.station_short_code = ?1)" +
            "   AND t.type = '0'" +
            "   AND (t.train_category_id in ?7)" +
            "   AND t.actual_time IS NULL" +
            "   AND (t.deleted IS NULL OR t.deleted = 0)" +
            " ORDER BY t.predict_time ASC" +
            " LIMIT ?5)) unionedTable", nativeQuery = true)
    List<Object[]> findLiveTrainsIds(String station, Integer departedTrains, Integer departingTrains, Integer arrivedTrains,
                                     Integer arrivingTrains, Boolean excludeNonstopping, List<Long> trainCategoryIds);


    @Query("select t from LiveTimeTableTrain t where " +
            " t.stationShortCode = ?1 and" +
            " t.trainCategoryId in ?8 and" +
            " t.version > ?5 and" +
            " (" +
            " ((t.trainStopping = true or t.trainStopping = ?4) and t.type = 1 and ((t.actualTime BETWEEN ?2 AND ?3) " +
            "   OR (t.actualTime IS NULL AND t.liveEstimateTime BETWEEN ?2 AND ?3)" +
            "   OR (t.actualTime IS NULL AND t.liveEstimateTime IS NULL AND t.scheduledTime BETWEEN ?2 AND ?3)))" +
            " OR " +
            " ((t.trainStopping = true or t.trainStopping = ?4) and t.type = 0 and ((t.actualTime BETWEEN ?6 AND ?7) " +
            "   OR (t.actualTime IS NULL AND t.liveEstimateTime BETWEEN ?6 AND ?7)" +
            "   OR (t.actualTime IS NULL AND t.liveEstimateTime IS NULL AND t.scheduledTime BETWEEN ?6 AND ?7)) " +
            " ) " +
            ")")
    List<LiveTimeTableTrain> findLiveTrains(String station, ZonedDateTime startDeparture, ZonedDateTime endDeparture,
                                            Boolean excludeNonstopping, Long version, ZonedDateTime startArrival, ZonedDateTime endArrival, List<Long> trainCategoryIds);

    @Query(value = "SELECT  " +
            "    '1',departure_date, train_number, MAX(version) " +
            "FROM " +
            "    (SELECT DISTINCT " +
            "        '1', " +
            "            t.departure_date, " +
            "            t.train_number, " +
            "            t.version, " +
            "            t.actual_time, " +
            "            t.predict_time, " +
            "            t.scheduled_time " +
            "    FROM " +
            "        live_time_table_train t " +
            "    WHERE " +
            "        (?1 = 0 OR t.version > ?1) " +
            "            AND ((t.actual_time BETWEEN (UTC_TIMESTAMP() - INTERVAL ?2 MINUTE) AND (UTC_TIMESTAMP() + INTERVAL ?2 MINUTE) " +
            "            OR t.predict_time BETWEEN (UTC_TIMESTAMP() - INTERVAL ?2 MINUTE) AND (UTC_TIMESTAMP() + INTERVAL ?2 MINUTE) " +
            "            OR t.scheduled_time BETWEEN (UTC_TIMESTAMP() - INTERVAL ?2 MINUTE) AND (UTC_TIMESTAMP() + INTERVAL ?2 MINUTE))) " +
            "    ORDER BY t.actual_time DESC , t.predict_time ASC) inner_table " +
            "GROUP BY departure_date , train_number " +
            " ", nativeQuery = true)
    List<Object[]> findLiveTrains(long version, int minutes);

    @Query(BASE_TRAIN_SELECT + " where train.id in (?1) and (?2 = true or " + IS_NOT_DELETED + ") " + BASE_TRAIN_ORDER)
    @Transactional(readOnly = true)
    List<Train> findTrains(Collection<TrainId> trainIds, Boolean include_deleted);

    @Query(BASE_TRAIN_SELECT + " where train.id in (?1) and " + IS_NOT_DELETED + " " + BASE_TRAIN_ORDER)
    @Transactional(readOnly = true)
    List<Train> findTrains(Collection<TrainId> trainIds);

    @Query(BASE_TRAIN_SELECT + " " +
            " where " +
            IS_NOT_DELETED + " and " +
            "   train.id.departureDate between ?7 and ?8 and" +
            "   train.id in ( " +
            "             select ttrDeparture.train.id" +
            "             from TimeTableRow ttrDeparture, TimeTableRow ttrArrival " +
            "             where " +
            "               ttrDeparture.train = train " +
            "               and ttrDeparture.station.stationShortCode = ?1 and ttrDeparture.type=?2 and (ttrDeparture.trainStopping = true or ttrDeparture.trainStopping = ?9)" +
            "               and ttrDeparture.scheduledTime between ?5 and ?6 " +
            "               and ttrArrival.train = train " +
            "               and ttrArrival.station.stationShortCode = ?3 and ttrArrival.type=?4 and (ttrArrival.trainStopping = true or ttrArrival.trainStopping = ?9)" +
            "               and ttrArrival.scheduledTime >= ttrDeparture.scheduledTime " +
            "               ) "
            + BASE_TRAIN_ORDER)
    List<Train> findByStationsAndScheduledDate(String departureStation, TimeTableRow.TimeTableRowType departure, String arrivalStation,
                                               final TimeTableRow.TimeTableRowType arrival, ZonedDateTime scheduledStart, ZonedDateTime scheduledEnd,
                                               final LocalDate departureDateStart, final LocalDate departureDateEnd, final Boolean includeNonstopping);

    @Query(BASE_TRAIN_SELECT + " where train.id.departureDate = ?1 and train.id.trainNumber = ?2 and (?3 = true or " + IS_NOT_DELETED + ") " + BASE_TRAIN_ORDER)
    Train findByDepartureDateAndTrainNumber(LocalDate departureDate, Long trainNumber, Boolean include_deleted);

    @Modifying
    @Query("DELETE FROM Train train WHERE train.id in ?1")
    void removeByTrainId(List<TrainId> trainIds);

    @Modifying
    @Query("DELETE FROM Train train WHERE train.id.departureDate = ?1")
    void removeByDepartureDate(LocalDate departureDate);

    @Query(value = "SELECT DISTINCT " +
            "    '1', ttr.departure_date, ttr.train_number, t.version, ttr.scheduled_time " +
            " FROM " +
            "    time_table_row ttr, train t " +
            "WHERE " +
            "   t.train_number = ttr.train_number and t.departure_date = ttr.departure_date" +
            "    AND ttr.train_number = ?1 " +
            "    AND ttr.departure_date BETWEEN DATE_ADD(current_date(), INTERVAL -1 DAY) AND DATE_ADD(current_date(), INTERVAL 1 DAY) " +
            "    AND ttr.scheduled_time BETWEEN (UTC_TIMESTAMP() - INTERVAL 4 HOUR) AND (UTC_TIMESTAMP() + INTERVAL 16 HOUR) " +
            "ORDER BY ABS(timediff(scheduled_time,UTC_TIMESTAMP())) ASC " +
            "LIMIT 1", nativeQuery = true)
    List<Object[]> findLiveTrainByTrainNumber(long trainNumber);

    @Query("select coalesce(max(train.version),0) from Train train")
    long getMaxVersion();

    @Query("select count(train) from Train train where train.id.departureDate = ?1")
    int countByDepartureDate(LocalDate departureDate);

    @Query("select distinct train.id,train.version from Train train where train.id.departureDate = ?1")
    List<Object[]> findByDepartureDateLite(LocalDate date);

    @Query("select distinct train from Train train left join fetch train.timeTableRows timeTableRow where train.id.departureDate = ?1")
    List<Train> findByDepartureDateFull(LocalDate date);

    @Query("select train from Train train where train.id.departureDate < ?1 and train.runningCurrently = true")
    List<Train> findRunningTrains(LocalDate maxDepartureDate);
}
