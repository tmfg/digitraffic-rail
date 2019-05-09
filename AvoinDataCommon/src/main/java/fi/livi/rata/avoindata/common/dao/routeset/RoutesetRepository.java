package fi.livi.rata.avoindata.common.dao.routeset;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.routeset.Routeset;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.ZonedDateTime;
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
            " order by t.messageId asc, rsec.sectionOrder asc")
    List<Routeset> findByTrainNumberAndDepartureDate(String trainNumber, LocalDate departureDate);

    @Query("SELECT distinct t FROM Routeset t left join fetch t.routesections rsec where " +
            " rsec.stationCode = ?1 and" +
            " (" +
            "   t.virtualDepartureDate = ?2 " +
            "   or" +
            "   (t.virtualDepartureDate = ?3 and t.trainId.departureDate is null and t.messageTime between ?4 and ?5)" +
            " ) order by t.id desc, rsec.sectionOrder asc")
    List<Routeset> findByStationAndDepartureDate(String station, LocalDate departureDate, final LocalDate nextDay,
                                                 final ZonedDateTime nextDayStart, final ZonedDateTime nextDayEnd);

    // This is faster but limiting does not work
    // @Query("SELECT distinct t FROM Routeset t left join fetch t.routesections rsec where t.version > ?1 order by t.version desc, rsec.sectionOrder asc ")
    @Query("SELECT distinct t FROM Routeset t where t.version > ?1 order by t.version asc")
    List<Routeset> findByVersionGreaterThan(long version, Pageable pageable);
}
