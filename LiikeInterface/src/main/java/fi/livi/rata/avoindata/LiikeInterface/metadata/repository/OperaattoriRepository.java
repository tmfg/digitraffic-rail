package fi.livi.rata.avoindata.LiikeInterface.metadata.repository;

import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Operaattori;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OperaattoriRepository extends JpaRepository<Operaattori, Long> {
}
