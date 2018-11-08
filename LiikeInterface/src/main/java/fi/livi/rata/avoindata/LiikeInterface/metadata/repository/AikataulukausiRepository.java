package fi.livi.rata.avoindata.LiikeInterface.metadata.repository;

import fi.livi.rata.avoindata.LiikeInterface.domain.entities.infra.Aikataulukausi;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Set;

@Repository
public interface AikataulukausiRepository extends JpaRepository<Aikataulukausi, Long> {
    @Query("select atk from Aikataulukausi atk inner join fetch atk.muutosajankohdat mua order by atk.voimassaAlkuPvm desc, mua.voimaantuloPvm desc")
    Set<Aikataulukausi> findAllAikataulukausi();
}
