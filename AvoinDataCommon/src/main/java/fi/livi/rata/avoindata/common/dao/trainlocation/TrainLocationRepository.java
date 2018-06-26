package fi.livi.rata.avoindata.common.dao.trainlocation;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;

@Repository
public interface TrainLocationRepository extends CustomGeneralRepository<TrainLocation, Long> {

    @Query("select max(tl.id) " +
            "   from TrainLocation tl " +
            "   where tl.trainLocationId.timestamp >= ?1 " +
            "   group by tl.trainLocationId.departureDate,tl.trainLocationId.trainNumber")
    List<Long> findLatest(ZonedDateTime timestampAfter);

    @Query("select max(tl.id) " +
            "   from TrainLocation tl " +
            "   where tl.trainLocationId.timestamp >= ?1 and tl.trainLocationId.trainNumber = ?2" +
            "   group by tl.trainLocationId.departureDate,tl.trainLocationId.trainNumber")
    List<Long> findLatestForATrain(ZonedDateTime timestampAfter, Long trainNumber);

    @Query("select tl from TrainLocation tl where tl.id in ?1 order by tl.trainLocationId.trainNumber asc")
    List<TrainLocation> findAllOrderByTrainNumber(List<Long> ids);

    @Query("select tl from TrainLocation tl where " +
            "tl.trainLocationId.trainNumber = ?1 and " +
            "tl.trainLocationId.departureDate = ?2 " +
            "order by tl.trainLocationId.timestamp desc")
    List<TrainLocation> findTrain(Long train_number, LocalDate departure_date);
}
