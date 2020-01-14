package fi.livi.rata.avoindata.server.controller.api.ruma;

import fi.livi.rata.avoindata.common.dao.trackwork.TrackWorkNotificationIdAndVersion;
import fi.livi.rata.avoindata.common.dao.trackwork.TrackWorkNotificationRepository;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.server.config.WebConfig;
import fi.livi.rata.avoindata.server.controller.api.ADataController;
import fi.livi.rata.avoindata.server.controller.utils.CacheControl;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static fi.livi.rata.avoindata.server.controller.utils.CacheControl.CACHE_AGE_DAY;

@Api(tags = "trackwork-notification", description = "Returns track work notifications")
@RestController
@RequestMapping(WebConfig.CONTEXT_PATH + "trackwork-notifications")
@Transactional(timeout = 30, readOnly = true)
public class TrackWorkNotificationController extends ADataController {

    public static final int CACHE_MAX_AGE_SECONDS = 30;
    public static final ZonedDateTime START_OF_TIME = ZonedDateTime.parse("2000-01-01T00:00:00Z");
    public static final ZonedDateTime END_OF_TIME = ZonedDateTime.parse("3000-12-31T23:59:59Z");

    @Autowired
    private TrackWorkNotificationRepository trackWorkNotificationRepository;

    @ApiOperation("Returns ids and latest versions of all trackwork notifications, limited to 500 results")
    @RequestMapping(method = RequestMethod.GET)
    public List<TrackWorkNotificationIdAndVersion> findAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime end) {
        return trackWorkNotificationRepository.findByModifiedBetween(start != null ? start : START_OF_TIME,
                end != null ? end : END_OF_TIME);
    }

    @ApiOperation("Returns all versions of a trackwork notification")
    @RequestMapping(method = RequestMethod.GET, path = "/{id}")
    public TrackWorkNotificationWithVersionsDto get(
            @PathVariable final int id,
            HttpServletResponse response) {
        final List<TrackWorkNotification> versions = trackWorkNotificationRepository.findByTwnId(id);
        CacheControl.setCacheMaxAgeSeconds(response, CACHE_MAX_AGE_SECONDS);
        return new TrackWorkNotificationWithVersionsDto(id, versions);
    }

    @ApiOperation("Returns a specific version of a trackwork notification")
    @RequestMapping(method = RequestMethod.GET, path = "/{id}/{version}")
    public Collection<TrackWorkNotification> getVersion(
            @PathVariable  final int id,
            @PathVariable  final int version,
            HttpServletResponse response) {
        final Optional<TrackWorkNotification> trackWorkNotification = trackWorkNotificationRepository.findByTwnIdAndVersion(id, version);
        if (trackWorkNotification.isEmpty()) {
            CacheControl.setCacheMaxAgeSeconds(response, CACHE_MAX_AGE_SECONDS);
            return Collections.emptyList();
        } else {
            CacheControl.setCacheMaxAgeSeconds(response, CACHE_AGE_DAY);
            return Collections.singleton(trackWorkNotification.get());
        }
    }

}
