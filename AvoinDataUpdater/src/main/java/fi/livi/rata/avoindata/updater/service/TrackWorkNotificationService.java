package fi.livi.rata.avoindata.updater.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.common.dao.trackwork.TrackWorkNotificationRepository;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;

@Service
public class TrackWorkNotificationService {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private TrackWorkNotificationRepository trackWorkNotificationRepository;

    @Transactional
    public void update(TrackWorkNotification[] trackWorkNotifications) {
        List<TrackWorkNotification> toBeSaved = new ArrayList<>();
        for (TrackWorkNotification trackWorkNotification : trackWorkNotifications) {
            if (!trackWorkNotificationRepository.existsByRumaIdAndRumaVersion(trackWorkNotification.rumaId, trackWorkNotification.rumaVersion)) {
                toBeSaved.add(trackWorkNotification);
            }
        }

        if (!toBeSaved.isEmpty()) {
            trackWorkNotificationRepository.saveAll(toBeSaved);
            log.info("Update data for {} TrackWorkNotifications", toBeSaved.size());

        }
    }
}
