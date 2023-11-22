package fi.livi.rata.avoindata.server.controller.api.ruma;

import static fi.livi.rata.avoindata.server.controller.utils.CacheControl.CACHE_AGE_DAY;
import static java.util.Collections.emptyList;

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
import fi.livi.rata.avoindata.common.dao.trafficrestriction.TrafficRestrictionNotificationRepository;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotification;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotificationState;
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

@Tag(name = "traffic-restriction-notifications", description = "Returns traffic restriction notifications")
@RestController
public class TrafficRestrictionNotificationController extends ADataController {

    public static final String PATH = WebConfig.CONTEXT_PATH + "trafficrestriction-notifications";
    private static final int MAX_RESULTS = 500;
    private static final int CACHE_MAX_AGE_SECONDS = 30;
    private static final int CACHE_UPDATE_CYCLE = 5 * 60;
    private static final Set<TrafficRestrictionNotificationState> DEFAULT_STATES = Set.of(
            TrafficRestrictionNotificationState.SENT,
            TrafficRestrictionNotificationState.FINISHED
    );

    @Autowired
    private TrafficRestrictionNotificationRepository trafficRestrictionNotificationRepository;

    @Operation(summary = "Returns ids and latest versions of all trafficrestriction notifications, limited to " + MAX_RESULTS + " results")
    @RequestMapping(method = RequestMethod.GET, path = PATH + "/status")
    @Transactional(timeout = 30, readOnly = true)
    public List<RumaNotificationIdAndVersion> getAllTrafficRestrictionNotifications(
            @Parameter(description = "Start time. If missing, current date - 7 days is used.", example = "2019-01-01T00:00:00.000Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final ZonedDateTime start,
            @Parameter(description = "End time. If missing, current date is used.", example = "2019-02-02T10:10:10.000Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final ZonedDateTime end) {
        return trafficRestrictionNotificationRepository.findByModifiedBetween(
                getStartTime(start),
                getEndTime(end),
                PageRequest.of(0, MAX_RESULTS));
    }

    @Operation(summary = "Returns all versions of a trafficrestriction notification or an empty list if the notification does not exist")
    @RequestMapping(method = RequestMethod.GET, path = PATH + "/{id}")
    @Transactional(timeout = 30, readOnly = true)
    public TrafficRestrictionNotificationWithVersions getTrafficRestrictionNotificationsById(
            @Parameter(description = "Traffic restriction notification identifier", required = true) @PathVariable final String id,
            @Parameter(description = "Show map or schema locations", schema = @Schema(type = "boolean", defaultValue = "false", example = "false"), example = "false")
            @RequestParam(value = "schema", required = false, defaultValue = "false") final Boolean schema,
            final HttpServletResponse response) {
        final List<TrafficRestrictionNotification> versions = trafficRestrictionNotificationRepository.findByTrnId(id);
        CacheControl.setCacheMaxAgeSeconds(response, CACHE_MAX_AGE_SECONDS);
        return new TrafficRestrictionNotificationWithVersions(id,
                versions.stream().map(trn -> RumaSerializationUtil.toTrnDto(trn, schema != null ? schema : false)).collect(Collectors.toList()));
    }

    @Operation(summary = "Returns the latest version of a trafficrestriction notification in JSON format or an empty list if the notification does not exist")
    @RequestMapping(method = RequestMethod.GET, path = PATH + "/{id}/latest.json")
    @Transactional(timeout = 30, readOnly = true)
    public Collection<SpatialTrafficRestrictionNotificationDto> getLatestTrafficRestrictionNotificationById(
            @Parameter(description = "Traffic restriction notification identifier", required = true) @PathVariable final String id,
            @Parameter(description = "Show map or schema locations", schema = @Schema(type = "boolean", defaultValue = "false", example = "false"), example = "false")
            @RequestParam(value = "schema", required = false, defaultValue = "false") final Boolean schema,
            final HttpServletResponse response) {
        final Optional<TrafficRestrictionNotification> trafficRestrictionNotification = trafficRestrictionNotificationRepository.findByTrnIdLatest(id);
        if (trafficRestrictionNotification.isEmpty()) {
            CacheControl.setCacheMaxAgeSeconds(response, CACHE_MAX_AGE_SECONDS);
            return emptyList();
        } else {
            CacheControl.setCacheMaxAgeSeconds(response, CACHE_AGE_DAY);
            return Collections.singleton(RumaSerializationUtil.toTrnDto(trafficRestrictionNotification.get(), schema != null ? schema : false));
        }
    }

    @Operation(summary = "Returns the latest version of a trafficrestriction notification in GeoJSON format or an empty FeatureCollection if the notification does not exist")
    @RequestMapping(method = RequestMethod.GET, path = PATH + "/{id}/latest.geojson")
    @Transactional(timeout = 30, readOnly = true)
    public FeatureCollection getLatestTrafficRestrictionNotificationByIdGeoJson(
            @Parameter(description = "Traffic restriction notification identifier", required = true) @PathVariable final String id,
            @Parameter(description = "Show map or schema locations", schema = @Schema(type = "boolean", defaultValue = "false", example = "false"), example = "false")
            @RequestParam(value = "schema", required = false, defaultValue = "false") final Boolean schema,
            final HttpServletResponse response) {
        final Optional<TrafficRestrictionNotification> trafficRestrictionNotification = trafficRestrictionNotificationRepository.findByTrnIdLatest(id);
        if (trafficRestrictionNotification.isEmpty()) {
            CacheControl.setCacheMaxAgeSeconds(response, CACHE_MAX_AGE_SECONDS);
            return new FeatureCollection(emptyList());
        } else {
            CacheControl.setCacheMaxAgeSeconds(response, CACHE_AGE_DAY);
            return new FeatureCollection(RumaSerializationUtil.toTrnFeatures(trafficRestrictionNotification.get(), schema != null ? schema : false).collect(Collectors.toList()));
        }
    }

    @Operation(summary = "Returns a specific version of a trafficrestriction notification or an empty list if the notification does not exist")
    @RequestMapping(method = RequestMethod.GET, path = PATH + "/{id}/{version}")
    @Transactional(timeout = 30, readOnly = true)
    public Collection<SpatialTrafficRestrictionNotificationDto> getTrafficRestrictionNotificationsByVersion(
            @Parameter(description = "Traffic restriction notification identifier", required = true) @PathVariable final String id,
            @Parameter(description = "Traffic restriction notification version", required = true) @PathVariable final long version,
            @Parameter(description = "Show map or schema locations", schema = @Schema(type = "boolean", defaultValue = "false", example = "false"), example = "false")
            @RequestParam(value = "schema", required = false, defaultValue = "false") final Boolean schema,
            final HttpServletResponse response) {
        final Optional<TrafficRestrictionNotification> trafficRestrictionNotification = trafficRestrictionNotificationRepository.findByTrnIdAndVersion(id, version);
        if (trafficRestrictionNotification.isEmpty()) {
            CacheControl.setCacheMaxAgeSeconds(response, CACHE_MAX_AGE_SECONDS);
            return emptyList();
        } else {
            CacheControl.setCacheMaxAgeSeconds(response, CACHE_AGE_DAY);
            return Collections.singleton(RumaSerializationUtil.toTrnDto(trafficRestrictionNotification.get(), schema != null ? schema : false));
        }
    }

    @Operation(summary = "Returns newest versions of trafficrestriction notifications by state in JSON format, limited to " + MAX_RESULTS + " results", ignoreJsonView = true)
    @RequestMapping(method = RequestMethod.GET, path = PATH + ".json", produces = "application/json")
    @JsonView(RumaJsonViews.PlainJsonView.class)
    @Transactional(timeout = 30, readOnly = true)
    public List<SpatialTrafficRestrictionNotificationDto> getTrafficRestrictionNotificationsByStateJson(
            @Parameter(description = "State of traffic restriction notification",
                       array = @ArraySchema(
                               schema = @Schema(implementation = TrafficRestrictionNotificationState.class,
                                                enumAsRef = true,
                                                type = "string")))
            @RequestParam(value = "state", required = false, defaultValue = "SENT,FINISHED") final Set<TrafficRestrictionNotificationState> state,
            @Parameter(description = "Show map or schema locations", schema = @Schema(type = "boolean", defaultValue = "false", example = "false"), example = "false")
            @RequestParam(value = "schema", required = false, defaultValue = "false") final Boolean schema,
            @Parameter(description = "Start time. If missing, current date - 7 days.", example = "2019-01-01T00:00:00.000Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final ZonedDateTime start,
            @Parameter(description = "End time. If missing, current date is used.", example = "2019-02-02T10:10:10.000Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final ZonedDateTime end,
            final HttpServletResponse response) {
        final List<SpatialTrafficRestrictionNotificationDto> twns = getByState(state, start, end)
                .stream()
                .map(t -> RumaSerializationUtil.toTrnDto(t, schema != null ? schema : false))
                .collect(Collectors.toList());
        CacheControl.setCacheMaxAgeSeconds(response, CACHE_UPDATE_CYCLE);
        return twns;
    }

    @Operation(summary = "Returns newest versions of trafficrestriction notifications by state in GeoJSON format, limited to " + MAX_RESULTS + " results", ignoreJsonView = true)
    @RequestMapping(method = RequestMethod.GET, path = PATH + ".geojson", produces = "application/vnd.geo+json")
    @JsonView(RumaJsonViews.GeoJsonView.class)
    @Transactional(timeout = 30, readOnly = true)
    public FeatureCollection getTrafficRestrictionNotificationsByStateGeoJson(
            @Parameter(description = "State of traffic restriction notification",
                       array = @ArraySchema(
                               schema = @Schema(implementation = TrafficRestrictionNotificationState.class,
                                                enumAsRef = true,
                                                type = "string")))
            @RequestParam(name = "state", required = false, defaultValue = "SENT,FINISHED") final Set<TrafficRestrictionNotificationState> state,
            @Parameter(description = "Show map or schema locations", schema = @Schema(type = "boolean", defaultValue = "false", example = "false"), example = "false")
            @RequestParam(name = "schema", required = false, defaultValue = "false") final Boolean schema,
            @Parameter(description = "Start time. If missing, current date - 7 days is used.", example = "2019-01-01T00:00:00.000Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final ZonedDateTime start,
            @Parameter(description = "End time. If missing, current date is used.", example = "2019-02-02T10:10:10.000Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final ZonedDateTime end,
            final HttpServletResponse response) {
        final FeatureCollection features = new FeatureCollection(getByState(state, start, end)
                .stream()
                .flatMap(t -> RumaSerializationUtil.toTrnFeatures(t, schema != null ? schema : false))
                .collect(Collectors.toList()));
        CacheControl.setCacheMaxAgeSeconds(response, CACHE_UPDATE_CYCLE);
        return features;
    }

    private List<TrafficRestrictionNotification> getByState(final Set<TrafficRestrictionNotificationState> state, final ZonedDateTime start, final ZonedDateTime end) {
        final Set<TrafficRestrictionNotificationState> states = state != null && !state.isEmpty() ? state : DEFAULT_STATES;
        return trafficRestrictionNotificationRepository.findByState(states.stream().map(s -> s.ordinal()).collect(Collectors.toSet()),
                getStartTime(start),
                getEndTime(end),
                PageRequest.of(0, MAX_RESULTS));
    }

    private ZonedDateTime getStartTime(final ZonedDateTime start) {
        return start != null ? start : ZonedDateTime.now().minusDays(7);
    }

    private ZonedDateTime getEndTime(final ZonedDateTime end) {
        return end != null ? end : ZonedDateTime.now();
    }

}
