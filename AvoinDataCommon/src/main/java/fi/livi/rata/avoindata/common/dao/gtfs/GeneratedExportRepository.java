package fi.livi.rata.avoindata.common.dao.gtfs;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;

@Repository
public interface GeneratedExportRepository extends CustomGeneralRepository<GeneratedExport, Long> {
        GeneratedExport findFirstByFileNameOrderByIdDesc(final String s);

        @Query("select gtfs.created from GeneratedExport gtfs where gtfs.fileName = ?1 order by gtfs.id desc limit 1")
        Optional<Instant> findLatestCreatedByFileName(final String fileName);

        /**
         * The latest export generated on a given day, for requesting an older package.
         */
        GeneratedExport findFirstByFileNameAndCreatedGreaterThanEqualAndCreatedLessThanOrderByIdDesc(
                        String fileName, ZonedDateTime from, ZonedDateTime until);

        @Query("select max(gtfs.id) from GeneratedExport gtfs group by gtfs.fileName")
        List<Long> findLatestIdPerFileName();

        @Query("delete from GeneratedExport gtfs where gtfs.created < ?1 and gtfs.id not in ?2")
        @Modifying
        Integer deleteOldZips(ZonedDateTime deleteBefore, Collection<Long> keepIds);

        @Query("select max(gtfs.id) from GeneratedExport gtfs where gtfs.fileName = ?1")
        Optional<Long> findLatestIdByFileName(String fileName);

        @Query("select max(gtfs.id) from GeneratedExport gtfs where gtfs.fileName = ?1 and gtfs.created < ?2")
        Optional<Long> findLatestIdByFileNameCreatedBefore(String fileName, ZonedDateTime before);

        /**
         * Ids of the oldest snapshots of one realtime feed, for deleting them with {@link #deleteByIds}. Deleting
         * them with a single DELETE ... ORDER BY id LIMIT made MySQL read every snapshot of every feed in between,
         * including SIRI-ET snapshots of about 12 MB each, and one batch took over ten minutes. This query reads
         * only the index on file_name, not the snapshot data.
         */
        @Query(value = "SELECT id FROM gtfs WHERE file_name = :fileName AND id <= :maxId ORDER BY id LIMIT :maxRows", nativeQuery = true)
        List<Long> findIdsByFileNameUpToId(@Param("fileName") String fileName, @Param("maxId") long maxId,
                        @Param("maxRows") int maxRows);

        @Transactional
        @Modifying
        @Query(value = "DELETE FROM gtfs WHERE id IN (:ids)", nativeQuery = true)
        int deleteByIds(@Param("ids") Collection<Long> ids);
}
