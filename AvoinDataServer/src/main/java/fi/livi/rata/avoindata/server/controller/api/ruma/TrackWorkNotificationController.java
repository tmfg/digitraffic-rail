package fi.livi.rata.avoindata.server.controller.api.ruma;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.livi.rata.avoindata.common.dao.trackwork.TrackWorkNotificationIdAndVersion;
import fi.livi.rata.avoindata.common.dao.trackwork.TrackWorkNotificationRepository;
import fi.livi.rata.avoindata.common.domain.spatial.GeometryUtils;
import fi.livi.rata.avoindata.common.domain.trackwork.RumaLocation;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotificationState;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkPart;
import fi.livi.rata.avoindata.server.config.WebConfig;
import fi.livi.rata.avoindata.server.controller.api.ADataController;
import fi.livi.rata.avoindata.server.controller.utils.CacheControl;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static fi.livi.rata.avoindata.server.controller.utils.CacheControl.CACHE_AGE_DAY;

@Api(tags = "track-work-notifications", description = "Returns track work notifications")
@RestController
@RequestMapping(WebConfig.CONTEXT_PATH + "trackwork-notifications")
@Transactional(timeout = 30, readOnly = true)
public class TrackWorkNotificationController extends ADataController {

    private static final int MAX_RESULTS = 500;
    private static final int CACHE_MAX_AGE_SECONDS = 30;
    private static final ZonedDateTime START_OF_TIME = ZonedDateTime.parse("2000-01-01T00:00:00Z");
    private static final ZonedDateTime END_OF_TIME = ZonedDateTime.parse("3000-12-31T23:59:59Z");
    private static final Set<TrackWorkNotificationState> DEFAULT_STATES = Set.of(
            TrackWorkNotificationState.SENT,
            TrackWorkNotificationState.ACTIVE,
            TrackWorkNotificationState.PASSIVE
    );

    @Autowired
    private TrackWorkNotificationRepository trackWorkNotificationRepository;

    @ApiOperation("Returns ids and latest versions of all trackwork notifications, limited to " + MAX_RESULTS + " results")
    @RequestMapping(method = RequestMethod.GET, path = "/status")
    public List<TrackWorkNotificationIdAndVersion> findAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime end) {
        return trackWorkNotificationRepository.findByModifiedBetween(start != null ? start : START_OF_TIME,
                end != null ? end : END_OF_TIME,
                PageRequest.of(0, MAX_RESULTS));
    }

    @ApiOperation("Returns all versions of a trackwork notification or an empty list if the notification does not exist")
    @RequestMapping(method = RequestMethod.GET, path = "/{id}")
    public TrackWorkNotificationWithVersions get(
            @PathVariable final int id,
            HttpServletResponse response) {
        final List<TrackWorkNotification> versions = trackWorkNotificationRepository.findByTwnId(id);
        CacheControl.setCacheMaxAgeSeconds(response, CACHE_MAX_AGE_SECONDS);
        return new TrackWorkNotificationWithVersions(id, versions);
    }

    @ApiOperation("Returns a specific version of a trackwork notification or an empty list if the notification does not exist")
    @RequestMapping(method = RequestMethod.GET, path = "/{id}/{version}")
    public Collection<TrackWorkNotification> getVersion(
            @PathVariable final int id,
            @PathVariable final int version,
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

    @RequestMapping(method = RequestMethod.GET, path = "foo.json", produces = "application/json")
    public List<TrackWorkNotificationDto> getByStateJson(@RequestParam(value = "state", required = false) final Set<TrackWorkNotificationState> state) throws Exception {
        final List<TrackWorkNotification> twns = getByState(state);
        return twns.stream().map(this::toTwnDto).collect(Collectors.toList());
    }

    private List<TrackWorkNotification> getByState(final Set<TrackWorkNotificationState> state) {
        Set<TrackWorkNotificationState> states = state != null ? state : DEFAULT_STATES;
        return trackWorkNotificationRepository.findByState(states);
    }

    private TrackWorkNotificationDto toTwnDto(TrackWorkNotification twn) {
        return new TrackWorkNotificationDto(twn.id,
                twn.state,
                twn.organization,
                twn.created,
                twn.modified,
                twn.trafficSafetyPlan,
                twn.speedLimitPlan,
                twn.speedLimitRemovalPlan,
                twn.electricitySafetyPlan,
                twn.personInChargePlan,
                GeometryUtils.fromJtsGeometry(twn.locationMap),
                GeometryUtils.fromJtsGeometry(twn.locationSchema),
                twn.trackWorkParts.stream().map(this::toWorkPartDto).collect(Collectors.toList()));
    }

    private TrackWorkPartDto toWorkPartDto(TrackWorkPart twp) {
        return new TrackWorkPartDto(twp.partIndex,
                twp.startDay,
                twp.permissionMinimumDuration,
                twp.containsFireWork,
                twp.plannedWorkingGap,
                twp.advanceNotifications,
                twp.locations.stream().map(this::toLocationDto).collect(Collectors.toSet()));
    }

    private RumaLocationDto toLocationDto(RumaLocation r) {
        return new RumaLocationDto(r.locationType,
                r.operatingPointId,
                r.sectionBetweenOperatingPointsId,
                r.locationMap != null ? GeometryUtils.fromJtsGeometry(r.locationMap) : null,
                r.locationSchema != null ? GeometryUtils.fromJtsGeometry(r.locationSchema) : null);
    }

}
