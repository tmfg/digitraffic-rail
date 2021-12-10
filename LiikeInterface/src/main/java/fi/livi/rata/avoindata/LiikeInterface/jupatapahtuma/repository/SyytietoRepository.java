package fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Syytieto;

@Repository
public interface SyytietoRepository extends CrudRepository<Syytieto, Long> {
    @Query("select syy.jupaTapahtuma.id.junanumero,syy.jupaTapahtuma.id.lahtopvm, max(syy.version) " +
            "from Syytieto syy " +
            "where syy.jupaTapahtuma.id.lahtopvm = ?1 " +
            "and syy.jupaTapahtuma.id.junanumero in ?2 " +
            "group by syy.jupaTapahtuma.id.junanumero,syy.jupaTapahtuma.id.lahtopvm")
    List<Object[]> findMaxVersions(LocalDate start, Collection<String> junanumeros);


}
