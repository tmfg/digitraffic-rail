package fi.livi.rata.avoindata.LiikeInterface.metadata.repository;

import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Junatyyppi;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JunatyyppiRepository extends JpaRepository<Junatyyppi, Long> {
}
