package fi.livi.rata.avoindata.common.dao.train;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.train.Train;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

import static fi.livi.rata.avoindata.common.dao.train.TrainRepository.BASE_TRAIN_ORDER;
import static fi.livi.rata.avoindata.common.dao.train.TrainRepository.BASE_TRAIN_SELECT;

@Repository
public interface AllTrainsRepository extends CustomGeneralRepository<Train, TrainId> {
    @Query(BASE_TRAIN_SELECT + " where train.id in (?1) " + BASE_TRAIN_ORDER)
    List<Train> findTrains(Collection<TrainId> trainIds);

    @Query("select t.id from Train t where " + "t.version > ?1 order by t.version asc")
    List<TrainId> findByVersionGreaterThan(Long version, Pageable pageable);

    @Query("select coalesce(max(train.version),0) from Train train")
    long getMaxVersion();
}
