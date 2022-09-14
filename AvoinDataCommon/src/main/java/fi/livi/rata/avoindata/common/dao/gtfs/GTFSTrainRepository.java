package fi.livi.rata.avoindata.common.dao.gtfs;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
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
}
