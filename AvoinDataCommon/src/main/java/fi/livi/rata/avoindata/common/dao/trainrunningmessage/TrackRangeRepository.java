package fi.livi.rata.avoindata.common.dao.trainrunningmessage;

import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.tracksection.TrackRange;

@Repository
public interface TrackRangeRepository extends CustomGeneralRepository<TrackRange, Long> {

}

