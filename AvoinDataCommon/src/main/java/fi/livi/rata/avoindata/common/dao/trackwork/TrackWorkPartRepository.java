package fi.livi.rata.avoindata.common.dao.trackwork;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkPart;
import org.springframework.stereotype.Repository;

@Repository
public interface TrackWorkPartRepository extends CustomGeneralRepository<TrackWorkPart, Long> {
}
