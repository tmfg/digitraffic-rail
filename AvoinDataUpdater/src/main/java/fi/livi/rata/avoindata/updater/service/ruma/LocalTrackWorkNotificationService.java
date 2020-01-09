package fi.livi.rata.avoindata.updater.service.ruma;

import fi.livi.rata.avoindata.common.dao.trackwork.TrackWorkNotificationIdAndVersion;
import fi.livi.rata.avoindata.common.dao.trackwork.TrackWorkNotificationRepository;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LocalTrackWorkNotificationService {

    @Autowired
    private TrackWorkNotificationRepository trackWorkNotificationRepository;

    @Transactional(readOnly = true)
    public List<LocalTrackWorkNotificationStatus> getLocalTrackWorkNotifications(Set<Integer> ids) {
        return trackWorkNotificationRepository.findIdsAndVersions(ids)
                .stream()
                .collect(Collectors.groupingBy(TrackWorkNotificationIdAndVersion::getId, Collectors.mapping(TrackWorkNotificationIdAndVersion::getVersion, Collectors.toList())))
                .entrySet()
                .stream()
                .map(e -> new LocalTrackWorkNotificationStatus(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    @Transactional
    public void saveAll(List<TrackWorkNotification> trackWorkNotifications) {
        trackWorkNotificationRepository.saveAll(trackWorkNotifications);
    }
}
