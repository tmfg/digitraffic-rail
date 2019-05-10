package fi.livi.rata.avoindata.common.dao.routeset;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.routeset.Routeset;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface RoutesetRepository extends CustomGeneralRepository<Routeset, Long> {
    @Query("select coalesce(max(t.version),0) from Routeset t")
    long getMaxVersion();

    @Query("delete from Routeset t where t.id in ?1")
    @Modifying
    void removeById(List<Long> ids);

    @Query("SELECT distinct t FROM Routeset t left join fetch t.routesections rsec where " +
            " t.trainId.trainNumber = ?1 and " +
            "   t.virtualDepartureDate = ?2 " +
            " order by t.version asc, rsec.sectionOrder asc")
    List<Routeset> findByTrainNumberAndDepartureDate(String trainNumber, LocalDate departureDate);


    @Query("SELECT distinct t FROM Routeset t left join fetch t.routesections rsec where " +
            " t.id in  ?1 " +
            " order by t.version asc, rsec.sectionOrder asc")
    List<Routeset> findAllById(List<Long> ids);

    @Query("SELECT distinct t.id FROM Routeset t left join t.routesections rsec where " +
            " rsec.stationCode = ?2 and " +
            " t.virtualDepartureDate = ?1")
    List<Long> findIdByStationAndDepartureDate(LocalDate departureDate, String station);

    @Query("SELECT distinct t.id FROM Routeset t where t.version > ?1 order by t.version asc")
    List<Long> findIdByVersionGreaterThan(long version, Pageable pageable);
}
