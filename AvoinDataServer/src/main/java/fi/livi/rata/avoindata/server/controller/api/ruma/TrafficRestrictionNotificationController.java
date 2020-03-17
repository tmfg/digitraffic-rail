package fi.livi.rata.avoindata.server.controller.api.ruma;

import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.common.dao.RumaNotificationIdAndVersion;
import fi.livi.rata.avoindata.common.dao.trafficrestriction.TrafficRestrictionNotificationRepository;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotification;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotificationState;
import fi.livi.rata.avoindata.server.config.WebConfig;
import fi.livi.rata.avoindata.server.controller.api.ADataController;
import fi.livi.rata.avoindata.server.controller.api.geojson.FeatureCollection;
import fi.livi.rata.avoindata.server.controller.utils.CacheControl;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static fi.livi.rata.avoindata.server.controller.utils.CacheControl.CACHE_AGE_DAY;
import static java.util.Collections.emptyList;

@Api(tags = "traffic-restriction-notifications", description = "Returns traffic restriction notifications")
@RestController
@Transactional(timeout = 30, readOnly = true)
public class TrafficRestrictionNotificationController extends ADataController {

    public static final String PATH = WebConfig.CONTEXT_PATH + "trafficrestriction-notifications";
    private static final int MAX_RESULTS = 500;
    private static final int CACHE_MAX_AGE_SECONDS = 30;
    private static final Set<TrafficRestrictionNotificationState> DEFAULT_STATES = Set.of(
            TrafficRestrictionNotificationState.SENT,
            TrafficRestrictionNotificationState.FINISHED
    );

    @Autowired
    private TrafficRestrictionNotificationRepository trafficRestrictionNotificationRepository;

    @ApiOperation("Returns ids and latest versions of all trafficrestriction notifications, limited to " + MAX_RESULTS + " results")
    @RequestMapping(method = RequestMethod.GET, path = PATH + "/status")
    public List<RumaNotificationIdAndVersion> findAll(
            @ApiParam(value = "Start time. If missing, start of time is used.", example = "2019-01-01T00:00:00.000Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime start,
            @ApiParam(value = "End time. If missing, end of time is used.", example = "2019-02-02T10:10:10.000Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime end) {
        return trafficRestrictionNotificationRepository.findByModifiedBetween(
                getStartTime(start),
                getEndTime(end),
                PageRequest.of(0, MAX_RESULTS));
    }

    @ApiOperation("Returns all versions of a trafficrestriction notification or an empty list if the notification does not exist")
    @RequestMapping(method = RequestMethod.GET, path = PATH + "/{id}")
    public TrafficRestrictionNotificationWithVersions get(
            @ApiParam(value = "Traffic restriction notification identifier", required = true) @PathVariable final String id,
            HttpServletResponse response) {
        final List<TrafficRestrictionNotification> versions = trafficRestrictionNotificationRepository.findByTrnId(id);
        CacheControl.setCacheMaxAgeSeconds(response, CACHE_MAX_AGE_SECONDS);
        return new TrafficRestrictionNotificationWithVersions(id, versions);
    }

    @ApiOperation("Returns a specific version of a trafficrestriction notification or an empty list if the notification does not exist")
    @RequestMapping(method = RequestMethod.GET, path = PATH + "/{id}/{version}")
    public Collection<TrafficRestrictionNotification> getVersion(
            @ApiParam(value = "Traffic restriction notification identifier", required = true) @PathVariable final String id,
            @ApiParam(value = "Traffic restriction notification version", required = true) @PathVariable final long version,
            HttpServletResponse response) {
        final Optional<TrafficRestrictionNotification> trafficRestrictionNotification = trafficRestrictionNotificationRepository.findByTrnIdAndVersion(id, version);
        if (trafficRestrictionNotification.isEmpty()) {
            CacheControl.setCacheMaxAgeSeconds(response, CACHE_MAX_AGE_SECONDS);
            return emptyList();
        } else {
            CacheControl.setCacheMaxAgeSeconds(response, CACHE_AGE_DAY);
            return Collections.singleton(trafficRestrictionNotification.get());
        }
    }

    @ApiOperation("Returns newest versions of trafficrestriction notifications by state in JSON format, limited to " + MAX_RESULTS + " results")
    @RequestMapping(method = RequestMethod.GET, path = PATH + ".json", produces = "application/json")
    @JsonView(RumaJsonViews.PlainJsonView.class)
    public List<SpatialTrafficRestrictionNotificationDto> getByStateJson(
            @ApiParam(defaultValue = "ACTIVE", value = "State of traffic restriction notification") @RequestParam(value = "state", required = false) final Set<TrafficRestrictionNotificationState> state,
            @ApiParam(defaultValue = "false", value = "Show map or schema locations") @RequestParam(value = "schema", required = false) final Boolean schema,
            @ApiParam(value = "Start time. If missing, current date - 7 days.", example = "2019-01-01T00:00:00.000Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime start,
            @ApiParam(value = "End time. If missing, current date is used.", example = "2019-02-02T10:10:10.000Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime end)
    {
        final List<TrafficRestrictionNotification> twns = getByState(state, start, end);
        return twns.stream().map(t -> RumaSerializationUtil.toTrnDto(t, schema != null ? schema : false)).collect(Collectors.toList());
    }

    @ApiOperation("Returns newest versions of trafficrestriction notifications by state in GeoJSON format, limited to " + MAX_RESULTS + " results")
    @RequestMapping(method = RequestMethod.GET, path = PATH + ".geojson", produces = "application/vnd.geo+json")
    @JsonView(RumaJsonViews.GeoJsonView.class)
    public FeatureCollection getByStateGeoJson(
            @ApiParam(defaultValue = "ACTIVE", value = "State of traffic restriction notification") @RequestParam(value = "state", required = false) final Set<TrafficRestrictionNotificationState> state,
            @ApiParam(defaultValue = "false", value = "Show map or schema locations") @RequestParam(value = "schema", required = false) final Boolean schema,
            @ApiParam(value = "Start time. If missing, current date - 7 days is used.", example = "2019-01-01T00:00:00.000Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime start,
            @ApiParam(value = "End time. If missing, current date is used.", example = "2019-02-02T10:10:10.000Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime end)
    {
        final List<TrafficRestrictionNotification> twns = getByState(state, start, end);
        return new FeatureCollection(twns.stream().flatMap(t -> RumaSerializationUtil.toTrnFeatures(t, schema != null ? schema : false)).collect(Collectors.toList()));
    }

    private List<TrafficRestrictionNotification> getByState(final Set<TrafficRestrictionNotificationState> state, ZonedDateTime start, ZonedDateTime end) {
        Set<TrafficRestrictionNotificationState> states = state != null ? state : DEFAULT_STATES;
        return trafficRestrictionNotificationRepository.findByState(states,
                getStartTime(start),
                getEndTime(end),
                PageRequest.of(0, MAX_RESULTS));
    }

    private ZonedDateTime getStartTime(ZonedDateTime start) {
        return start != null ? start : ZonedDateTime.now().minusDays(7);
    }

    private ZonedDateTime getEndTime(ZonedDateTime end) {
        return end != null ? end : ZonedDateTime.now();
    }

}
