package fi.livi.rata.avoindata.common.dao.train;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.train.Forecast;

@Repository
public interface ForecastRepository extends CustomGeneralRepository<Forecast, Long> {

    @Query("delete from Forecast t where t.id in ?1")
    @Modifying
    void removeById(List<Long> idsToRemove);

    @Query("select coalesce(max(f.version),0) from Forecast f")
    long getMaxVersion();

    @Query("select forecast from Forecast forecast " +
            " inner join fetch forecast.timeTableRow ttr " +
            " inner join fetch ttr.train t" +
            " where t.id in ?1")
    List<Forecast> findByTrains(Collection<TrainId> trains);
}
