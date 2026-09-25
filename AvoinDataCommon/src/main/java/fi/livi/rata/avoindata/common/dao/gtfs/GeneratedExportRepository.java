package fi.livi.rata.avoindata.common.dao.gtfs;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;

@Repository
public interface GeneratedExportRepository extends CustomGeneralRepository<GeneratedExport, Long> {
    GeneratedExport findFirstByFileNameOrderByIdDesc(String s);

    /**
     * The latest export generated on a given day, for requesting an older package.
     */
    GeneratedExport findFirstByFileNameAndCreatedGreaterThanEqualAndCreatedLessThanOrderByIdDesc(
            String fileName, ZonedDateTime from, ZonedDateTime until);

    /**
     * The rows the API serves — excluded from pruning so a feed is never left with
     * nothing to serve.
     */
    @Query("select max(gtfs.id) from GeneratedExport gtfs group by gtfs.fileName")
    List<Long> findLatestIdPerFileName();

    @Query("delete from GeneratedExport gtfs where gtfs.created < ?1 and gtfs.id not in ?2")
    @Modifying
    Integer deleteOldZips(ZonedDateTime deleteBefore, Collection<Long> keepIds);
}
