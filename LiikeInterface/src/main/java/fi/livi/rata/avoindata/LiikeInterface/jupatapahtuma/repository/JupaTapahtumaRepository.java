package fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.repository;

import fi.livi.rata.avoindata.LiikeInterface.domain.JunatapahtumaPrimaryKey;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.JupaTapahtuma;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;

@Repository
public interface JupaTapahtumaRepository extends CrudRepository<JupaTapahtuma, JunatapahtumaPrimaryKey> {
    @Query("select coalesce(max(t.version),0) from JupaTapahtuma t where t.muokkausAika >= ?1")
    long getMaxVersion(ZonedDateTime minMuokkausaika);
}
