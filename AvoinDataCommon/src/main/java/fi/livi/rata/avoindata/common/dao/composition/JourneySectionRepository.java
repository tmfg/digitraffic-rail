package fi.livi.rata.avoindata.common.dao.composition;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.composition.JourneySection;
import org.springframework.stereotype.Repository;

@Repository
public interface JourneySectionRepository extends CustomGeneralRepository<JourneySection, Long> {
}
