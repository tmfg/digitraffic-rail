package fi.livi.rata.avoindata.common.dao.gtfs;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrip;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTripId;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface GTFSTripRepository extends CustomGeneralRepository<GTFSTrip, GTFSTripId> {
    @Query(value = "select max(version) from gtfs_trip", nativeQuery = true)
    Long getMaxVersion();
}
