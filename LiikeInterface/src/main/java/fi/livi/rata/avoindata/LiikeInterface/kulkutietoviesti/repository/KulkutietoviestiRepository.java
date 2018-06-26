package fi.livi.rata.avoindata.LiikeInterface.kulkutietoviesti.repository;

import fi.livi.rata.avoindata.LiikeInterface.domain.entities.kulkutietoviesti.Kulkutietoviesti;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface KulkutietoviestiRepository extends CrudRepository<Kulkutietoviesti, Long> {
    @Query("select ktv from Kulkutietoviesti ktv where ktv.tapahtumaV = ?1")
    List<Kulkutietoviesti> findByLahtopvm(LocalDate date);

    @Query("select ktv from Kulkutietoviesti ktv where ktv.tapahtumaPvm > ?2 and ktv.version > ?1")
    List<Kulkutietoviesti> findByVersioGreaterThan(Long version, ZonedDateTime minimumTapahtumaPvm);
}
