package fi.livi.rata.avoindata.common.dao.composition;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.composition.Wagon;
import org.springframework.stereotype.Repository;

@Repository
public interface WagonRepository extends CustomGeneralRepository<Wagon, Long> {
}
