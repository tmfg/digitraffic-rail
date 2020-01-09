package fi.livi.rata.avoindata.server.controller.api;

import fi.livi.rata.avoindata.common.dao.trackwork.TrackWorkNotificationRepository;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.server.config.WebConfig;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Optional;

@Api(tags = "trackwork-notification", description = "Returns track work notifications")
@RestController
@RequestMapping(WebConfig.CONTEXT_PATH + "trackwork-notifications")
@Transactional(timeout = 30, readOnly = true)
public class TrackWorkNotificationController extends ADataController {

    @Autowired
    private TrackWorkNotificationRepository trackWorkNotificationRepository;

    @ApiOperation("Returns all trackwork notifications")
    @RequestMapping(method = RequestMethod.GET)
    public List<TrackWorkNotification> findAll(HttpServletResponse response) {
        final List<TrackWorkNotification> trackWorkNotifications = trackWorkNotificationRepository.findAll();
        //CacheConfig.COMPOSITION_CACHECONTROL.setCacheParameter(response, compositions, version);
        return trackWorkNotifications;
    }

    @ApiOperation("Returns a specific trackwork notification")
    @RequestMapping(method = RequestMethod.GET, path = "/{trackwork-notification-id}")
    public TrackWorkNotification get(
            @PathVariable("trackwork-notification-id") final int trackworkNotificationId,
            HttpServletResponse response) {
        //final Optional<TrackWorkNotification> trackWorkNotification = trackWorkNotificationRepository.findById(trackworkNotificationId);
        //CacheConfig.COMPOSITION_CACHECONTROL.setCacheParameter(response, compositions, version);
        //return trackWorkNotification.orElse(null);
        return null;
    }

}
