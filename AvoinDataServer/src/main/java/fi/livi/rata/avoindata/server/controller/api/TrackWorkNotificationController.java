package fi.livi.rata.avoindata.server.controller.api;

import fi.livi.rata.avoindata.common.dao.trackwork.TrackWorkNotificationRepository;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.server.config.WebConfig;
import fi.livi.rata.avoindata.server.controller.utils.CacheControl;
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

import static fi.livi.rata.avoindata.server.controller.api.TrainLocationController.CACHE_MAX_AGE;
import static fi.livi.rata.avoindata.server.controller.utils.CacheControl.CACHE_AGE_DAY;

@Api(tags = "trackwork-notification", description = "Returns track work notifications")
@RestController
@RequestMapping(WebConfig.CONTEXT_PATH + "trackwork-notifications")
@Transactional(timeout = 30, readOnly = true)
public class TrackWorkNotificationController extends ADataController {

    public static final int CACHE_MAX_AGE_SECONDS = 30;

    @Autowired
    private TrackWorkNotificationRepository trackWorkNotificationRepository;

    @ApiOperation("Returns all trackwork notifications")
    @RequestMapping(method = RequestMethod.GET)
    public List<TrackWorkNotification> findAll(HttpServletResponse response) {
        final List<TrackWorkNotification> trackWorkNotifications = trackWorkNotificationRepository.findAll();
        CacheControl.setCacheMaxAgeSeconds(response, CACHE_MAX_AGE);
        return trackWorkNotifications;
    }

    @ApiOperation("Returns all versions of a trackwork notification")
    @RequestMapping(method = RequestMethod.GET, path = "/{id}")
    public List<TrackWorkNotification> get(
            @PathVariable final int id,
            HttpServletResponse response) {
        final List<TrackWorkNotification> trackWorkNotifications = trackWorkNotificationRepository.findByTwnId(id);
        if (trackWorkNotifications.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return null;
        } else {
            CacheControl.setCacheMaxAgeSeconds(response, CACHE_MAX_AGE_SECONDS);
            return trackWorkNotifications;
        }
    }

    @ApiOperation("Returns a specific version of a trackwork notification")
    @RequestMapping(method = RequestMethod.GET, path = "/{id}/{version}")
    public TrackWorkNotification getVersion(
            @PathVariable  final int id,
            @PathVariable  final int version,
            HttpServletResponse response) {
        final Optional<TrackWorkNotification> trackWorkNotification = trackWorkNotificationRepository.findByTwnIdAndVersion(id, version);
        if (trackWorkNotification.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return null;
        } else {
            CacheControl.setCacheMaxAgeSeconds(response, CACHE_AGE_DAY);
            return trackWorkNotification.get();
        }
    }

}
