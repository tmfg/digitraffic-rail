package fi.livi.rata.avoindata.common.dao.trackwork;

import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;

@Repository
public interface TrackWorkNotificationRepository extends CustomGeneralRepository<TrackWorkNotification, Long> {

    boolean existsByRumaIdAndRumaVersion(Integer rumaId, Integer rumaVersion);
}
