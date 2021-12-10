package fi.livi.rata.avoindata.LiikeInterface.purkaja.repository;

import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Aikataulu;
import fi.livi.rata.avoindata.LiikeInterface.purkaja.entity.Peruminen;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PeruminenRepository extends CrudRepository<Peruminen, Long> {
    @Query("select p from Peruminen p" +
            " left join fetch p.aikataulurivis " +
            "where p.aikataulu in ?1")
    List<Peruminen> findByAikatauluIn(List<Aikataulu> aikatauluList);
}
