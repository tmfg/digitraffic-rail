package fi.livi.rata.avoindata.server.controller.api.ruma;

import static fi.livi.rata.avoindata.server.controller.utils.CacheControl.CACHE_AGE_DAY;
import static java.util.Collections.emptyList;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.common.dao.RumaNotificationIdAndVersion;
import fi.livi.rata.avoindata.common.dao.trackwork.TrackWorkNotificationRepository;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotificationState;
import fi.livi.rata.avoindata.server.config.WebConfig;
import fi.livi.rata.avoindata.server.controller.api.ADataController;
import fi.livi.rata.avoindata.server.controller.api.geojson.FeatureCollection;
import fi.livi.rata.avoindata.server.controller.utils.CacheControl;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@Api(tags = "track-work-notifications", description = "Returns track work notifications")
@RestController
@Transactional(timeout = 30, readOnly = true)
public class TrackWorkNotificationController extends ADataController {

    public static final String PATH = WebConfig.CONTEXT_PATH + "trackwork-notifications";
    private static final int MAX_RESULTS = 500;
    private static final int CACHE_MAX_AGE_SECONDS = 30;
    private static final int CACHE_UPDATE_CYCLE = 5 * 60;
    private static final Set<TrackWorkNotificationState> DEFAULT_STATES = Set.of(
            TrackWorkNotificationState.SENT,
            TrackWorkNotificationState.ACTIVE,
            TrackWorkNotificationState.PASSIVE
    );

    @Autowired
    private TrackWorkNotificationRepository trackWorkNotificationRepository;

    @ApiOperation("Returns ids and latest versions of all trackwork notifications, limited to " + MAX_RESULTS + " results")
    @RequestMapping(method = RequestMethod.GET, path = PATH + "/status")
    public List<RumaNotificationIdAndVersion> getAllTrackWorkNotifications(
            @ApiParam(value = "Start time. If missing, current date - 7 days is used.", example = "2019-01-01T00:00:00.000Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime start,
            @ApiParam(value = "End time. If missing, current date is used", example = "2019-02-02T10:10:10.000Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime end) {
        return trackWorkNotificationRepository.findByModifiedBetween(
                getStartTime(start),
                getEndTime(end),
                PageRequest.of(0, MAX_RESULTS));
    }

    @ApiOperation("Returns all versions of a trackwork notification or an empty list if the notification does not exist")
    @RequestMapping(method = RequestMethod.GET, path = PATH + "/{id}")
    public TrackWorkNotificationWithVersions getTrackWorkNotificationsById(
            @ApiParam(value = "Track work notification identifier", required = true) @PathVariable final String id,
            @ApiParam(defaultValue = "false", value = "Show map or schema locations") @RequestParam(value = "schema", required = false) final Boolean schema,
            HttpServletResponse response) {
        final List<TrackWorkNotification> versions = trackWorkNotificationRepository.findByTwnId(id);
        CacheControl.setCacheMaxAgeSeconds(response, CACHE_MAX_AGE_SECONDS);
        return new TrackWorkNotificationWithVersions(id,
                versions.stream().map(twn -> RumaSerializationUtil.toTwnDto(twn, schema != null ? schema : false)).collect(Collectors.toList()));
    }

    @ApiOperation("Returns the latest version of a trackwork notification in JSON format or an empty list if the notification does not exist")
    @RequestMapping(method = RequestMethod.GET, path = PATH + "/{id}/latest.json")
    public Collection<SpatialTrackWorkNotificationDto> getLatestTrackWorkNotificationById(
            @ApiParam(value = "Track work notification identifier", required = true) @PathVariable final String id,
            @ApiParam(defaultValue = "false", value = "Show map or schema locations") @RequestParam(value = "schema", required = false) final Boolean schema,
            HttpServletResponse response) {
        final Optional<TrackWorkNotification> trackWorkNotification = trackWorkNotificationRepository.findByTwnIdLatest(id);
        if (trackWorkNotification.isEmpty()) {
            CacheControl.setCacheMaxAgeSeconds(response, CACHE_MAX_AGE_SECONDS);
            return emptyList();
        } else {
            CacheControl.setCacheMaxAgeSeconds(response, CACHE_AGE_DAY);
            return Collections.singleton(RumaSerializationUtil.toTwnDto(trackWorkNotification.get(), schema != null ? schema : false));
        }
    }

    @ApiOperation("Returns the latest version of a trackwork notification in GeoJSON format or an empty FeatureCollection if the notification does not exist")
    @RequestMapping(method = RequestMethod.GET, path = PATH + "/{id}/latest.geojson")
    public FeatureCollection getLatestTrackWorkNotificationByIdGeoJson(
            @ApiParam(value = "Track work notification identifier", required = true) @PathVariable final String id,
            @ApiParam(defaultValue = "false", value = "Show map or schema locations") @RequestParam(value = "schema", required = false) final Boolean schema,
            HttpServletResponse response) {

        final Optional<TrackWorkNotification> trackWorkNotification = trackWorkNotificationRepository.findByTwnIdLatest(id);
        if (trackWorkNotification.isEmpty()) {
            CacheControl.setCacheMaxAgeSeconds(response, CACHE_MAX_AGE_SECONDS);
            return new FeatureCollection(emptyList());
        } else {
            CacheControl.setCacheMaxAgeSeconds(response, CACHE_AGE_DAY);
            return new FeatureCollection(RumaSerializationUtil.toTwnFeatures(trackWorkNotification.get(), schema != null ? schema : false).collect(Collectors.toList()));
        }
    }

    @ApiOperation("Returns a specific version of a trackwork notification or an empty list if the notification does not exist")
    @RequestMapping(method = RequestMethod.GET, path = PATH + "/{id}/{version}")
    public Collection<SpatialTrackWorkNotificationDto> getTrackWorkNotificationsByIdAndVersion(
            @ApiParam(value = "Track work notification identifier", required = true) @PathVariable final String id,
            @ApiParam(defaultValue = "false", value = "Show map or schema locations") @RequestParam(value = "schema", required = false) final Boolean schema,
            @ApiParam(value = "Track work notification version", required = true) @PathVariable final long version,
            HttpServletResponse response) {
        final Optional<TrackWorkNotification> trackWorkNotification = trackWorkNotificationRepository.findByTwnIdAndVersion(id, version);
        if (trackWorkNotification.isEmpty()) {
            CacheControl.setCacheMaxAgeSeconds(response, CACHE_MAX_AGE_SECONDS);
            return emptyList();
        } else {
            CacheControl.setCacheMaxAgeSeconds(response, CACHE_AGE_DAY);
            return Collections.singleton(RumaSerializationUtil.toTwnDto(trackWorkNotification.get(), schema != null ? schema : false));
        }
    }

    @ApiOperation("Returns newest versions of trackwork notifications by state in JSON format, limited to " + MAX_RESULTS + " results")
    @RequestMapping(method = RequestMethod.GET, path = PATH + ".json", produces = "application/json")
    @JsonView(RumaJsonViews.PlainJsonView.class)
    public List<SpatialTrackWorkNotificationDto> getTrackWorkNotificationsByStateJson(
            @ApiParam(defaultValue = "SENT,ACTIVE,PASSIVE", value = "State of track work notification") @RequestParam(value = "state", required = false) final Set<TrackWorkNotificationState> state,
            @ApiParam(defaultValue = "false", value = "Show map or schema locations") @RequestParam(value = "schema", required = false) final Boolean schema,
            @ApiParam(value = "Start time. If missing, current date - 7 days is used.", example = "2019-01-01T00:00:00.000Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime start,
            @ApiParam(value = "End time. If missing, current date is used.", example = "2019-02-02T10:10:10.000Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime end,
            final HttpServletResponse response) {
        final List<SpatialTrackWorkNotificationDto> twns = getByState(state, start, end)
                .stream()
                .map(t -> RumaSerializationUtil.toTwnDto(t, schema != null ? schema : false))
                .collect(Collectors.toList());
        CacheControl.setCacheMaxAgeSeconds(response, CACHE_UPDATE_CYCLE);
        return twns;
    }

    @ApiOperation("Returns newest versions of trackwork notifications by state in GeoJSON format, limited to " + MAX_RESULTS + " results")
    @RequestMapping(method = RequestMethod.GET, path = PATH + ".geojson", produces = "application/vnd.geo+json")
    @JsonView(RumaJsonViews.GeoJsonView.class)
    public FeatureCollection getTrackWorkNotificationsByStateGeoJson(
            @ApiParam(defaultValue = "SENT,ACTIVE,PASSIVE", value = "State of track work notification") @RequestParam(value = "state", required = false) final Set<TrackWorkNotificationState> state,
            @ApiParam(defaultValue = "false", value = "Show map or schema locations") @RequestParam(value = "schema", required = false) final Boolean schema,
            @ApiParam(value = "Start time. If missing, current date - 7 days is used.", example = "2019-01-01T00:00:00.000Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime start,
            @ApiParam(value = "End time. If missing, current date is used.", example = "2019-02-02T10:10:10.000Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime end,
            final HttpServletResponse response) {
        final FeatureCollection features = new FeatureCollection(getByState(state, start, end).stream().flatMap(t -> RumaSerializationUtil.toTwnFeatures(t, schema != null ? schema : false)).collect(Collectors.toList()));
        CacheControl.setCacheMaxAgeSeconds(response, CACHE_UPDATE_CYCLE);
        return features;
    }

    private List<TrackWorkNotification> getByState(final Set<TrackWorkNotificationState> state, ZonedDateTime start, ZonedDateTime end) {
        Set<TrackWorkNotificationState> states = state != null && !state.isEmpty() ? state : DEFAULT_STATES;
        ZonedDateTime startTime = getStartTime(start);
        ZonedDateTime endTime = getEndTime(end);

        Duration duration = Duration.between(endTime, start);
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Duration between start and end time is negative");
        }
        if (duration.toDays() > 30) {
            throw new IllegalArgumentException("Duration between start and end time is more than 30 days");
        }

        return trackWorkNotificationRepository.findByState(states,
                startTime,
                endTime,
                PageRequest.of(0, MAX_RESULTS));
    }

    private ZonedDateTime getStartTime(ZonedDateTime start) {
        return start != null ? start : ZonedDateTime.now().minusDays(7);
    }

    private ZonedDateTime getEndTime(ZonedDateTime end) {
        return end != null ? end : ZonedDateTime.now();
    }

}
