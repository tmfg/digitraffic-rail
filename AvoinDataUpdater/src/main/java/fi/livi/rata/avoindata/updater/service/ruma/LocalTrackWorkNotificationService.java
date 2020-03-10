package fi.livi.rata.avoindata.updater.service.ruma;

import fi.livi.rata.avoindata.common.dao.RumaNotificationIdAndVersion;
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
    public List<LocalRumaNotificationStatus> getLocalTrackWorkNotifications(Set<String> ids) {
        return trackWorkNotificationRepository.findIdsAndVersions(ids)
                .stream()
                .collect(Collectors.groupingBy(RumaNotificationIdAndVersion::getId, Collectors.mapping(RumaNotificationIdAndVersion::getVersion, Collectors.toList())))
                .entrySet()
                .stream()
                .map(e -> new LocalRumaNotificationStatus(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TrackWorkNotification> getById(String id) {
        return trackWorkNotificationRepository.findByTwnId(id);
    }

    @Transactional
    public void saveAll(List<TrackWorkNotification> trackWorkNotifications) {
        trackWorkNotificationRepository.saveAll(trackWorkNotifications);
    }
}
