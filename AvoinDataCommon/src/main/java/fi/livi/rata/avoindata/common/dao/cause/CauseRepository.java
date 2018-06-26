package fi.livi.rata.avoindata.common.dao.cause;

import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.cause.Cause;

@Repository
public interface CauseRepository extends CustomGeneralRepository<Cause, Long> {
}
