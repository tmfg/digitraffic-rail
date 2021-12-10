package fi.livi.rata.avoindata.LiikeInterface.metadata.repository;

import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Liikennepaikka;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LiikennepaikkaRepository extends JpaRepository<Liikennepaikka, Long> {
    @Query(value = "SELECT * " +
            "FROM liikennepaikka lp " +
            "WHERE lp.lptyp_id IN (1,2,3) " +
            "AND lp.INFRA_ID    = ?1 " +
            "ORDER BY lp.nimi", nativeQuery = true)
    List<Liikennepaikka> findAllLiikennepaikkaOrSeisakeByInfraId(Long infraId);

    @Query("select distinct lp from Liikennepaikka lp " +
            "left join fetch lp.liikennepaikanLiikennepaikkaValis lp_lpval " +
            "left join fetch lp_lpval.liikennepaikkavali lpval " +
            "where lp.infraId = ?1")
    List<Liikennepaikka> findByInfraId(Long infraId);
}
