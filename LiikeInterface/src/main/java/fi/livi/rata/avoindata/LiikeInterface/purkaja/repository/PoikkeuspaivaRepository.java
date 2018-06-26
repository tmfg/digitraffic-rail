package fi.livi.rata.avoindata.LiikeInterface.purkaja.repository;

import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Aikataulu;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Poikkeuspaiva;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PoikkeuspaivaRepository extends CrudRepository<Poikkeuspaiva, Long> {

    List<Poikkeuspaiva> findByAikatauluIn(List<Aikataulu> aikatauluList);
}
