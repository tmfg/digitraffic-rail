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
            " inner join train.timeTableRows as row" +
            " where train.version > ?1" +
            " and train.id.departureDate between (current_date - 1) and current_date" +
            " and row.id.departureDate between (current_date - 1) and current_date")
    List<GTFSTrain> findByVersionGreaterThan(final long version);
}
