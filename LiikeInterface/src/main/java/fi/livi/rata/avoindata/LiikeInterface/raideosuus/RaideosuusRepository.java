package fi.livi.rata.avoindata.LiikeInterface.raideosuus;


import fi.livi.rata.avoindata.LiikeInterface.domain.entities.kulkutietoviesti.Raideosuus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RaideosuusRepository extends CrudRepository<Raideosuus, Long> {
    @Query("select distinct r from Raideosuus r join fetch r.raideosuudenSijaintis join fetch r.liikennepaikka where r.liikennepaikka.infraId = ?1")
    List<Raideosuus> findNewest(Long infraId);
}
