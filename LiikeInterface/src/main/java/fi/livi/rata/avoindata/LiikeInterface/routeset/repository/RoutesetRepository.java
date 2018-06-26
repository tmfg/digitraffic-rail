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
    @Query("select distinct rs from Routeset rs inner join fetch rs.routesections where " +
            "rs.departureDate = ?1 or " +
            "(rs.departureDate is null and rs.messageTime between ?2 and ?3)")
    List<Routeset> findByLahtopvm(LocalDate date, ZonedDateTime start, ZonedDateTime end);

    @Query("select distinct rs from Routeset rs inner join fetch rs.routesections where " +
            " rs.version > ?1 and rs.id > (select max(rs_a.id)-25000 from Routeset rs_a)")
    List<Routeset> findByVersioGreaterThan(Long version);
}
