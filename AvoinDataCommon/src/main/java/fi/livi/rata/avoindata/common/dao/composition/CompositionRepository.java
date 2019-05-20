package fi.livi.rata.avoindata.common.dao.composition;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.composition.Composition;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Repository
@Transactional
public interface CompositionRepository extends CustomGeneralRepository<Composition, TrainId> {
    String SELECT_COMPOSITION = "select distinct composition from Composition composition " +
            "inner join fetch composition.journeySections journeySections " +
            "inner join fetch journeySections.beginTimeTableRow beginTimeTableRow " +
            "inner join fetch journeySections.endTimeTableRow endTimeTableRow " +
            "inner join fetch journeySections.locomotives locomotives " +
            "left join fetch journeySections.wagons wagons ";

    String COMPOSITION_ORDER = "order by composition.id.departureDate, composition.id.trainNumber, journeySections.beginTimeTableRow.scheduledTime";

    @Query(SELECT_COMPOSITION +
            "where composition.id.departureDate = ?1 " +
            COMPOSITION_ORDER)
    List<Composition> findByDepartureDateBetweenOrderByTrainNumber(LocalDate date);

    @Query(SELECT_COMPOSITION +
            "where composition.id in ?1 " +
            COMPOSITION_ORDER)
    List<Composition> findByIds(List<TrainId> trainIds);

    @Query("select composition.id from Composition composition " +
            "where composition.version > ?1 " +
            "order by composition.version asc ")
    List<TrainId> findIdsByVersionGreaterThan(Long version, Pageable pageable);

    @Query("select coalesce(max(composition.version),0) from Composition composition")
    long getMaxVersion();

    @Query("select coalesce(max(composition.version),0) from Composition composition where composition.id.departureDate = ?1")
    long getMaxVersion(LocalDate dayToGetMaxVersionFrom);

    @Modifying
    @Query("delete from Composition comp where comp.id.departureDate = ?1 AND comp.id.trainNumber = ?2")
    void deleteWithId(LocalDate departureDate, Long trainNumber);

    @Modifying
    @Query("DELETE FROM Composition comp WHERE comp.id.departureDate = ?1")
    void removeByDepartureDate(LocalDate departureDate);
}
