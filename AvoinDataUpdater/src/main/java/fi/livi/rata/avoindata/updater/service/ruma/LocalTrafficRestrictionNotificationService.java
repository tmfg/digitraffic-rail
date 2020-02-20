package fi.livi.rata.avoindata.updater.service.ruma;

import fi.livi.rata.avoindata.common.dao.RumaNotificationIdAndVersion;
import fi.livi.rata.avoindata.common.dao.trafficrestriction.TrafficRestrictionNotificationRepository;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LocalTrafficRestrictionNotificationService {

    @Autowired
    private TrafficRestrictionNotificationRepository trafficRestrictionNotificationRepository;

    @Transactional(readOnly = true)
    public List<LocalRumaNotificationStatus> getLocalTrafficRestrictionNotifications(Set<Long> ids) {
        return trafficRestrictionNotificationRepository.findIdsAndVersions(ids)
                .stream()
                .collect(Collectors.groupingBy(RumaNotificationIdAndVersion::getId, Collectors.mapping(RumaNotificationIdAndVersion::getVersion, Collectors.toList())))
                .entrySet()
                .stream()
                .map(e -> new LocalRumaNotificationStatus(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TrafficRestrictionNotification> getById(long id) {
        return trafficRestrictionNotificationRepository.findByTrnId(id);
    }

    @Transactional
    public void saveAll(List<TrafficRestrictionNotification> trafficRestrictionNotifications) {
        trafficRestrictionNotificationRepository.saveAll(trafficRestrictionNotifications);
    }
}
