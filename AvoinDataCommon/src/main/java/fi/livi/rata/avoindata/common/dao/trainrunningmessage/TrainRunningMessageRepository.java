package fi.livi.rata.avoindata.common.dao.trainrunningmessage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessage;

@Repository
public interface TrainRunningMessageRepository extends CustomGeneralRepository<TrainRunningMessage, Long> {
    @Query("SELECT t FROM TrainRunningMessage t where " +
            " t.trainId.trainNumber = ?1 and " +
            " t.version > ?3 and" +
            " (" +
            "   t.virtualDepartureDate = ?2 " +
            "   or" +
            "   (t.virtualDepartureDate = ?4 and t.trainId.departureDate is null and t.timestamp between ?5 and ?6)" +
            " ) order by t.id desc")
    List<TrainRunningMessage> findByTrainNumberAndDepartureDate(String trainNumber, LocalDate departureDate, final Long version,
            final LocalDate nextDay, final ZonedDateTime nextDayStart, final ZonedDateTime nextDayEnd);

    @Query("SELECT t FROM TrainRunningMessage t where " +
            " t.station = ?1 and" +
            " (" +
            "   t.virtualDepartureDate = ?2 " +
            "   or" +
            "   (t.virtualDepartureDate = ?3 and t.trainId.departureDate is null and t.timestamp between ?4 and ?5)" +
            " ) order by t.id desc")
    List<TrainRunningMessage> findByStationAndDepartureDate(String station, LocalDate departureDate, final LocalDate nextDay,
            final ZonedDateTime nextDayStart, final ZonedDateTime nextDayEnd);

    @Query("SELECT t FROM TrainRunningMessage t where " +
            " t.station = ?1 and" +
            " t.trackSection = ?2 and" +
            " (" +
            "   t.virtualDepartureDate = ?3 " +
            "   or" +
            "   (t.virtualDepartureDate = ?4 and t.trainId.departureDate is null and t.timestamp between ?5 and ?6)" +
            " ) order by t.id desc")
    List<TrainRunningMessage> findByStationAndTrackSectionAndDepartureDate(String station, String trackSection, LocalDate departureDate,
            final LocalDate nextDay, final ZonedDateTime nextDayStart, final ZonedDateTime nextDayEnd);

    @Query("SELECT t FROM TrainRunningMessage t where " +
            " t.station = ?1 and" +
            " t.trackSection = ?2 " +
            "  order by t.virtualDepartureDate desc, t.id desc")
    List<TrainRunningMessage> findByStationAndTrackSection(String station, String trackSection, Pageable pageable);

    @Query("select coalesce(max(t.version),0) from TrainRunningMessage t")
    long getMaxVersion();

    @Query("delete from TrainRunningMessage t where t.id in ?1")
    @Modifying
    void removeById(List<Long> ids);

    @Query("SELECT max(t.virtualDepartureDate) FROM TrainRunningMessage t " +
            "where t.trainId.trainNumber = ?1 and t.virtualDepartureDate > ?2" +
            " order by t.id desc")
    LocalDate getMaxDepartureDateForTrainNumber(String trainNumber, LocalDate localDate);

    @Query("SELECT t FROM TrainRunningMessage t where t.version > ?1 order by t.version desc  ")
    List<TrainRunningMessage> findByVersionGreaterThan(long version, Pageable pageable);
}
