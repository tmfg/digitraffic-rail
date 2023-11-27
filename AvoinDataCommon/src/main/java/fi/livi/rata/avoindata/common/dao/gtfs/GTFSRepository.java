package fi.livi.rata.avoindata.common.dao.gtfs;

import java.time.ZonedDateTime;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFS;

@Repository
public interface GTFSRepository extends CustomGeneralRepository<GTFS, Long> {
    GTFS findFirstByFileNameOrderByIdDesc(String s);

    @Query("delete from GTFS gtfs where gtfs.created < ?1")
    @Modifying
    Integer deleteOldZips(ZonedDateTime deleteBefore);
}
