package fi.livi.rata.avoindata.common.dao.gtfs;

import java.time.ZonedDateTime;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;

@Repository
public interface GeneratedExportRepository extends CustomGeneralRepository<GeneratedExport, Long> {
    GeneratedExport findFirstByFileNameOrderByIdDesc(String s);

    @Query("delete from GeneratedExport gtfs where gtfs.created < ?1")
    @Modifying
    Integer deleteOldZips(ZonedDateTime deleteBefore);
}
