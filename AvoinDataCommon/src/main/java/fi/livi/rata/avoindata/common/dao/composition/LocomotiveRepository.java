package fi.livi.rata.avoindata.common.dao.composition;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.composition.Locomotive;
import org.springframework.stereotype.Repository;

@Repository
public interface LocomotiveRepository extends CustomGeneralRepository<Locomotive, Long> {
}
