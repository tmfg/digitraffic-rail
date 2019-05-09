package fi.livi.rata.avoindata.LiikeInterface.routeset.repository;

import fi.livi.rata.avoindata.LiikeInterface.domain.entities.routeset.Routeset;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface RoutesetRepository extends CrudRepository<Routeset, Long> {
    @Query("select distinct rs " +
            "from Routeset rs inner join fetch rs.routesections rsec " +
            "where " +
            "   rs.messageId != 'LIFE' and " +
            "   (rs.departureDate = ?1 or " +
            "   (rs.departureDate is null and rs.messageTime between ?2 and ?3)) " +
            "order by rs.messageId asc, rsec.sectionOrder asc")
    List<Routeset> findByLahtopvm(LocalDate date, ZonedDateTime start, ZonedDateTime end);

    @Query("select distinct rs " +
            "from Routeset rs inner join fetch rs.routesections rsec " +
            "where " +
            " rs.messageId != 'LIFE' and " +
            " rs.version > ?1 and " +
            " rs.departureDate > CURRENT_DATE - 2 " +
            "order by rs.messageId asc, rsec.sectionOrder asc")
    List<Routeset> findByVersioGreaterThan(Long version);
}
