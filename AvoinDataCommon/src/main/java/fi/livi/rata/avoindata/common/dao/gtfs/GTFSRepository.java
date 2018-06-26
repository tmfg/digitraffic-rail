package fi.livi.rata.avoindata.common.dao.gtfs;

import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFS;

@Repository
public interface GTFSRepository extends CustomGeneralRepository<GTFS, Long> {
    GTFS findFirstByFileNameOrderByIdDesc(String s);
}