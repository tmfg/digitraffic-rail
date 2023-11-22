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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

@Tag(name = "track-work-notifications", description = "Returns track work notifications")
@RestController
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

    @Operation(summary = "Returns ids and latest versions of all trackwork notifications, limited to " + MAX_RESULTS + " results")
    @RequestMapping(method = RequestMethod.GET, path = PATH + "/status")
    @Transactional(timeout = 30, readOnly = true)
    public List<RumaNotificationIdAndVersion> getAllTrackWorkNotifications(
            @Parameter(description = "Start time. If missing, current date - 7 days is used.", example = "2019-01-01T00:00:00.000Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final ZonedDateTime start,
            @Parameter(description = "End time. If missing, current date is used", example = "2019-02-02T10:10:10.000Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final ZonedDateTime end) {
        return trackWorkNotificationRepository.findByModifiedBetween(
                getStartTime(start),
                getEndTime(end),
                PageRequest.of(0, MAX_RESULTS));
    }

    @Operation(summary = "Returns all versions of a trackwork notification or an empty list if the notification does not exist")
    @RequestMapping(method = RequestMethod.GET, path = PATH + "/{id}")
    @Transactional(timeout = 30, readOnly = true)
    public TrackWorkNotificationWithVersions getTrackWorkNotificationsById(
            @Parameter(description = "Track work notification identifier", required = true) @PathVariable final String id,
            @Parameter(description = "Show map or schema locations", example = "false") @RequestParam(value = "schema", required = false, defaultValue = "false") final Boolean schema,
            final HttpServletResponse response) {
        final List<TrackWorkNotification> versions = trackWorkNotificationRepository.findByTwnId(id);
        CacheControl.setCacheMaxAgeSeconds(response, CACHE_MAX_AGE_SECONDS);
        return new TrackWorkNotificationWithVersions(id,
                versions.stream().map(twn -> RumaSerializationUtil.toTwnDto(twn, schema != null ? schema : false)).collect(Collectors.toList()));
    }

    @Operation(summary = "Returns the latest version of a trackwork notification in JSON format or an empty list if the notification does not exist")
    @RequestMapping(method = RequestMethod.GET, path = PATH + "/{id}/latest.json")
    @Transactional(timeout = 30, readOnly = true)
    public Collection<SpatialTrackWorkNotificationDto> getLatestTrackWorkNotificationById(
            @Parameter(description = "Track work notification identifier", required = true) @PathVariable final String id,
            @Parameter(description = "Show map or schema locations", example = "false") @RequestParam(value = "schema", required = false, defaultValue = "false") final Boolean schema,
            final HttpServletResponse response) {
        final Optional<TrackWorkNotification> trackWorkNotification = trackWorkNotificationRepository.findByTwnIdLatest(id);
        if (trackWorkNotification.isEmpty()) {
            CacheControl.setCacheMaxAgeSeconds(response, CACHE_MAX_AGE_SECONDS);
            return emptyList();
        } else {
            CacheControl.setCacheMaxAgeSeconds(response, CACHE_AGE_DAY);
            return Collections.singleton(RumaSerializationUtil.toTwnDto(trackWorkNotification.get(), schema != null ? schema : false));
        }
    }

    @Operation(summary = "Returns the latest version of a trackwork notification in GeoJSON format or an empty FeatureCollection if the notification does not exist")
    @RequestMapping(method = RequestMethod.GET, path = PATH + "/{id}/latest.geojson")
    @Transactional(timeout = 30, readOnly = true)
    public FeatureCollection getLatestTrackWorkNotificationByIdGeoJson(
            @Parameter(description = "Track work notification identifier", required = true) @PathVariable final String id,
            @Parameter(description = "Show map or schema locations", example = "false") @RequestParam(value = "schema", required = false, defaultValue = "false") final Boolean schema,
            final HttpServletResponse response) {

        final Optional<TrackWorkNotification> trackWorkNotification = trackWorkNotificationRepository.findByTwnIdLatest(id);
        if (trackWorkNotification.isEmpty()) {
            CacheControl.setCacheMaxAgeSeconds(response, CACHE_MAX_AGE_SECONDS);
            return new FeatureCollection(emptyList());
        } else {
            CacheControl.setCacheMaxAgeSeconds(response, CACHE_AGE_DAY);
            return new FeatureCollection(RumaSerializationUtil.toTwnFeatures(trackWorkNotification.get(), schema != null ? schema : false).collect(Collectors.toList()));
        }
    }

    @Operation(summary = "Returns a specific version of a trackwork notification or an empty list if the notification does not exist")
    @RequestMapping(method = RequestMethod.GET, path = PATH + "/{id}/{version}")
    @Transactional(timeout = 30, readOnly = true)
    public Collection<SpatialTrackWorkNotificationDto> getTrackWorkNotificationsByIdAndVersion(
            @Parameter(description = "Track work notification identifier", required = true) @PathVariable final String id,
            @Parameter(description = "Show map or schema locations", example = "false") @RequestParam(value = "schema", required = false, defaultValue = "false") final Boolean schema,
            @Parameter(description = "Track work notification version", required = true) @PathVariable final long version,
            final HttpServletResponse response) {
        final Optional<TrackWorkNotification> trackWorkNotification = trackWorkNotificationRepository.findByTwnIdAndVersion(id, version);
        if (trackWorkNotification.isEmpty()) {
            CacheControl.setCacheMaxAgeSeconds(response, CACHE_MAX_AGE_SECONDS);
            return emptyList();
        } else {
            CacheControl.setCacheMaxAgeSeconds(response, CACHE_AGE_DAY);
            return Collections.singleton(RumaSerializationUtil.toTwnDto(trackWorkNotification.get(), schema != null ? schema : false));
        }
    }

    @Operation(summary = "Returns newest versions of trackwork notifications by state in JSON format, limited to " + MAX_RESULTS + " results", ignoreJsonView = true)
    @RequestMapping(method = RequestMethod.GET, path = PATH + ".json", produces = "application/json")
    @JsonView(RumaJsonViews.PlainJsonView.class)
    @Transactional(timeout = 30, readOnly = true)
    public List<SpatialTrackWorkNotificationDto> getTrackWorkNotificationsByStateJson(
            @Parameter(description = "State of track work notification", example = "SENT,ACTIVE,PASSIVE",
                       array = @ArraySchema(
                               schema = @Schema(enumAsRef = true, implementation = TrackWorkNotificationState.class)))
            @RequestParam(value = "state", required = false, defaultValue = "SENT,ACTIVE,PASSIVE") final Set<TrackWorkNotificationState> state,
            @Parameter(description = "Show map or schema locations", example = "false",
                       schema = @Schema(example = "false", defaultValue = "false", type = "boolean"))
            @RequestParam(value = "schema", required = false, defaultValue = "false") final Boolean schema,
            @Parameter(description = "Start time. If missing, current date - 7 days is used.", example = "2019-01-01T00:00:00.000Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final ZonedDateTime start,
            @Parameter(description = "End time. If missing, current date is used.", example = "2019-02-02T10:10:10.000Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final ZonedDateTime end,
            final HttpServletResponse response) {
        final List<SpatialTrackWorkNotificationDto> twns = getByState(state, start, end)
                .stream()
                .map(t -> RumaSerializationUtil.toTwnDto(t, schema != null ? schema : false))
                .collect(Collectors.toList());
        CacheControl.setCacheMaxAgeSeconds(response, CACHE_UPDATE_CYCLE);
        return twns;
    }

    @Operation(summary = "Returns newest versions of trackwork notifications by state in GeoJSON format, limited to " + MAX_RESULTS + " results", ignoreJsonView = true)
    @RequestMapping(method = RequestMethod.GET, path = PATH + ".geojson", produces = "application/vnd.geo+json")
    @JsonView(RumaJsonViews.GeoJsonView.class)
    @Transactional(timeout = 30, readOnly = true)
    public FeatureCollection getTrackWorkNotificationsByStateGeoJson(
            @Parameter(description = "State of track work notification", example = "SENT,ACTIVE,PASSIVE",
                       array = @ArraySchema(
                               schema = @Schema(enumAsRef = true, implementation = TrackWorkNotificationState.class)))
            @RequestParam(value = "state", required = false, defaultValue = "SENT,ACTIVE,PASSIVE") final Set<TrackWorkNotificationState> state,
            @Parameter(description = "Show map or schema locations", example = "false",
                       schema = @Schema(example = "false", defaultValue = "false", type = "boolean"))
            @RequestParam(value = "schema", required = false, defaultValue = "false") final Boolean schema,
            @Parameter(description = "Start time. If missing, current date - 7 days is used.", example = "2019-01-01T00:00:00.000Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final ZonedDateTime start,
            @Parameter(description = "End time. If missing, current date is used.", example = "2019-02-02T10:10:10.000Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final ZonedDateTime end,
            final HttpServletResponse response) {
        final FeatureCollection features = new FeatureCollection(getByState(state, start, end).stream().flatMap(t -> RumaSerializationUtil.toTwnFeatures(t, schema != null ? schema : false)).collect(Collectors.toList()));
        CacheControl.setCacheMaxAgeSeconds(response, CACHE_UPDATE_CYCLE);
        return features;
    }

    private List<TrackWorkNotification> getByState(final Set<TrackWorkNotificationState> state, final ZonedDateTime start, final ZonedDateTime end) {
        final Set<TrackWorkNotificationState> states = state != null && !state.isEmpty() ? state : DEFAULT_STATES;
        final ZonedDateTime startTime = getStartTime(start);
        final ZonedDateTime endTime = getEndTime(end);

        final Duration duration = Duration.between(startTime, endTime);
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Duration between start and end time is negative");
        }
        if (duration.toDays() > 30) {
            throw new IllegalArgumentException("Duration between start and end time is more than 30 days");
        }

        final List<TrackWorkNotification.TrackWorkNotificationId> ids =
                trackWorkNotificationRepository.findLatestBetween(startTime, endTime).stream()
                        .map(o -> new TrackWorkNotification.TrackWorkNotificationId((String)o[0], (Long)o[1]))
                        .toList();

        return trackWorkNotificationRepository.findByStateAndId(states, startTime, endTime, ids);
    }

    private ZonedDateTime getStartTime(final ZonedDateTime start) {
        return start != null ? start : ZonedDateTime.now().minusDays(7);
    }

    private ZonedDateTime getEndTime(final ZonedDateTime end) {
        return end != null ? end : ZonedDateTime.now();
    }

}
