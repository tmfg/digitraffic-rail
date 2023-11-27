package fi.livi.rata.avoindata.common.dao.train;

import static fi.livi.rata.avoindata.common.dao.train.TrainRepository.BASE_TRAIN_ORDER;
import static fi.livi.rata.avoindata.common.dao.train.TrainRepository.BASE_TRAIN_SELECT;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.train.Train;

@Repository
public interface AllTrainsRepository extends CustomGeneralRepository<Train, TrainId> {
    @Query(BASE_TRAIN_SELECT + " where (train.id.departureDate in ?2) and (train.id in ?1) " + BASE_TRAIN_ORDER)
    List<Train> findTrains(final Collection<TrainId> trainIds, final Collection<LocalDate> departureDates);

    @Query(nativeQuery = true, value = "select train_number, departure_date, version from train where version > ?1 order by version limit ?2")
    List<Object[]> findByVersionGreaterThanRawSql(Long version, int limit);

    @Query("select coalesce(max(train.version),0) from Train train")
    long getMaxVersion();
}
