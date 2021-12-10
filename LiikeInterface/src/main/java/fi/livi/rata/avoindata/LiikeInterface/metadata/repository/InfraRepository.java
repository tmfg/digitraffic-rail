package fi.livi.rata.avoindata.LiikeInterface.metadata.repository;

import fi.livi.rata.avoindata.LiikeInterface.domain.entities.infra.Infra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface InfraRepository extends JpaRepository<Infra, Long> {
    @Query("select a.infra from Aikataulukausi a where ?1 between a.voimassaAlkuPvm and a.voimassaLoppuPvm")
    Infra findInfraByDate(LocalDate date);
}
