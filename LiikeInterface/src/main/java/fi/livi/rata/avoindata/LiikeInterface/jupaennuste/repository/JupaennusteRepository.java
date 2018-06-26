package fi.livi.rata.avoindata.LiikeInterface.jupaennuste.repository;

import fi.livi.rata.avoindata.LiikeInterface.domain.entities.JupaEnnuste;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface JupaennusteRepository extends CrudRepository<JupaEnnuste, Long> {
    @Query("select u from JupaEnnuste u where " +
            " u.jupaTapahtumaId.lahtopvm = ?1 ")
    List<JupaEnnuste> findByLahtoPvm(LocalDate start);

    @Query("select u from JupaEnnuste u where " +
            " u.version > ?1 and" +
            " u.jupaTapahtumaId.lahtopvm >= ?2 ")
    List<JupaEnnuste> findByVersion(Long version, LocalDate startDate);
}
