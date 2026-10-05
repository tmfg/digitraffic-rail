package fi.livi.rata.avoindata.updater.service.netex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import fi.livi.digitraffic.common.util.TimeUtil;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiQuay;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStop;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiUicMatcher;

/**
 * Turns station metadata into NeTEx ScheduledStopPoints, RoutePoints and DestinationDisplays,
 * and matches them against PETI to produce PassengerStopAssignments.
 */
@Service
public class NeTExStopsService {

    private static final Logger log = LoggerFactory.getLogger(NeTExStopsService.class);

    private final NeTExIdGenerator idGenerator;
    private final PetiStopSource petiStopSource;

    public NeTExStopsService(final NeTExIdGenerator idGenerator, final PetiStopSource petiStopSource) {
        this.idGenerator = idGenerator;
        this.petiStopSource = petiStopSource;
    }

    /**
     * Builds the stop data for one generation: a ScheduledStopPoint for every station and
     * track the schedules use, a RoutePoint and a DestinationDisplay for every station, and a
     * PassengerStopAssignment pointing at the PETI quay whenever one matches the track.
     *
     * @param stations          station names and UIC codes; locations all come from PETI
     * @param stationTrackPairs station and track taken from the schedules
     */
    public NeTExStopsData createStopsData(final List<Station> stations,
            final List<StationTrackPair> stationTrackPairs) {
        final Map<String, Station> stationByShortCode = stations.stream()
                .filter(s -> s.passengerTraffic)
                .collect(Collectors.toMap(s -> s.shortCode, Function.identity(), (a, b) -> a));

        final var uniquePairs = new LinkedHashSet<>(stationTrackPairs);

        final List<NeTExStopsData.NeTExScheduledStopPoint> stopPoints = new ArrayList<>();
        final List<NeTExStopsData.NeTExRoutePoint> routePoints = new ArrayList<>();
        final List<NeTExStopsData.NeTExDestinationDisplay> destinationDisplays = new ArrayList<>();
        final List<NeTExStopsData.NeTExStopAssignment> stopAssignments = new ArrayList<>();

        final boolean petiSourceEmpty = petiStopSource.getStops().isEmpty();
        final PetiUicMatcher matcher = petiStopSource.getMatcher();
        int matchedCount = 0;
        int unmatchedCount = 0;
        int quayMatchedCount = 0;
        int quayUnmatchedCount = 0;
        int quayNoTrackCount = 0;

        final var seenStations = new LinkedHashSet<String>();
        final Map<String, String> projectionTargets = new HashMap<>();
        final Map<String, Station> stationsWithoutStopPlace = new LinkedHashMap<>();
        final Map<String, Station> stationsWithoutQuays = new LinkedHashMap<>();

        for (final StationTrackPair pair : uniquePairs) {
            final Station station = stationByShortCode.get(pair.stationShortCode());
            if (station == null) {
                continue;
            }

            // the assignment resolves the quay, whose centroid is the only real geography
            // we have for a platform
            final AssignmentResult result = petiSourceEmpty
                    ? AssignmentResult.none()
                    : buildAssignment(pair, station, matcher);

            final var stopPoint = buildScheduledStopPoint(pair, station);
            stopPoints.add(stopPoint);
            // lowest id wins so the projection target cannot drift between generations;
            // a station-level point sorts ahead of every platform of the same station
            projectionTargets.merge(station.shortCode, stopPoint.id(),
                    (a, b) -> a.compareTo(b) <= 0 ? a : b);
            if (seenStations.add(station.shortCode)) {
                destinationDisplays.add(new NeTExStopsData.NeTExDestinationDisplay(
                        idGenerator.destinationDisplayId(station.shortCode), publicStationName(station.name)));
            }

            if (!petiSourceEmpty) {
                result.assignment().ifPresent(stopAssignments::add);
                switch (result.outcome()) {
                    case MATCHED_QUAY -> {
                        matchedCount++;
                        quayMatchedCount++;
                    }
                    // Both of these mean the PETI stop place lists no platforms at all: if it
                    // listed any, the track was already swapped for the first one earlier in
                    // the run and would resolve here.
                    case MATCHED_NO_QUAY -> {
                        matchedCount++;
                        quayUnmatchedCount++;
                        stationsWithoutQuays.putIfAbsent(station.shortCode, station);
                    }
                    case MATCHED_NO_TRACK -> {
                        matchedCount++;
                        quayNoTrackCount++;
                        stationsWithoutQuays.putIfAbsent(station.shortCode, station);
                    }

                    case UNMATCHED -> {
                        unmatchedCount++;
                        if (stationsWithoutStopPlace.putIfAbsent(station.shortCode, station) == null) {
                            log.error("event=generateNeTEx method=createStopsData PETI publishes no stop place "
                                    + "for station station={} uic={} country={}",
                                    station.shortCode, station.uicCode, station.countryCode);
                        }
                    }
                }
            }
        }

        for (final String shortCode : seenStations) {
            routePoints.add(new NeTExStopsData.NeTExRoutePoint(
                    idGenerator.routePointId(shortCode), shortCode, projectionTargets.get(shortCode)));
        }

        if (!stationsWithoutQuays.isEmpty()) {
            log.error("event=generateNeTEx method=createStopsData gap=stationsWithoutQuays "
                    + "loggedAt={} stationsWithoutQuays={} gapItems={}",
                    TimeUtil.nowWithoutMillis(),
                    stationsWithoutQuays.size(),
                    stationsWithoutQuays.values().stream()
                            .map(s -> s.shortCode + "(uicCode:" + s.uicCode + ")")
                            .collect(Collectors.joining(",")));
        }

        if (!stationsWithoutStopPlace.isEmpty()) {
            log.error("event=generateNeTEx method=createStopsData gap=stationsWithoutStopPlace "
                    + "loggedAt={} stationsWithoutStopPlace={} gapItems={}",
                    TimeUtil.nowWithoutMillis(),
                    stationsWithoutStopPlace.size(),
                    stationsWithoutStopPlace.values().stream()
                            .map(s -> s.shortCode + "(uicCode:" + s.uicCode + ")")
                            .collect(Collectors.joining(",")));
        }

        return new NeTExStopsData(stopPoints, routePoints, destinationDisplays,
                stopAssignments, matchedCount, unmatchedCount,
                quayMatchedCount, quayUnmatchedCount, quayNoTrackCount);
    }

    /**
     * Station names in the rail metadata carry an " asema" ("station") suffix that
     * is better left out. Anchored on whitespace so compounds such as
     * "Lentoasema" and "Pasila autojuna-asema" are left alone.
     */
    public static String publicStationName(final String stationName) {
        return stationName == null ? null : stationName.replaceAll("\\s+asema$", "");
    }

    /**
     * The stop point carries no coordinates of its own. Its location comes from the quay its
     * PassengerStopAssignment points at.
     */
    private NeTExStopsData.NeTExScheduledStopPoint buildScheduledStopPoint(final StationTrackPair pair,
            final Station station) {
        return new NeTExStopsData.NeTExScheduledStopPoint(
                idGenerator.scheduledStopPointId(pair.stationShortCode(), pair.commercialTrack()),
                publicStationName(station.name), station.shortCode);
    }

    /**
     * Finds the PETI quay for one station and track. Returns the assignment linking the stop
     * point to that quay, or an empty result saying why none could be made.
     */
    private AssignmentResult buildAssignment(final StationTrackPair pair, final Station station,
            final PetiUicMatcher matcher) {
        final Optional<PetiStop> petiMatch = matcher.match(station.uicCode);
        if (petiMatch.isEmpty()) {
            return new AssignmentResult(Optional.empty(), Optional.empty(), MatchOutcome.UNMATCHED);
        }

        final PetiStop matched = petiMatch.get();
        final String track = pair.commercialTrack();
        final String sspId = idGenerator.scheduledStopPointId(pair.stationShortCode(), track);
        final String assignmentId = idGenerator.passengerStopAssignmentId(pair.stationShortCode(), track);

        if (track == null || track.isBlank()) {
            // A matched station takes its first platform as a track upstream, so a track is
            // only
            // blank here when the stop place publishes no quays at all — nothing to assign.
            return new AssignmentResult(Optional.empty(), Optional.empty(), MatchOutcome.MATCHED_NO_TRACK);
        }

        final Optional<PetiQuay> quay = matched.resolveQuay(track);
        if (quay.isPresent()) {
            return new AssignmentResult(
                    Optional.of(new NeTExStopsData.NeTExStopAssignment(
                            assignmentId, sspId, quay.get().quayId())),
                    quay,
                    MatchOutcome.MATCHED_QUAY);
        }

        // the schedule names a track PETI does not know, so there is no quay to assign
        log.error("event=generateNeTEx method=buildAssignment PETI quay not found for track station={} "
                + "uic={} track={} stopPlace={} petiTracks={} assignment={}",
                pair.stationShortCode(), station.uicCode, track,
                matched.stopPlaceId(),
                matched.quays().stream().map(PetiQuay::publicCode).collect(Collectors.joining(",")),
                assignmentId);

        return new AssignmentResult(Optional.empty(), Optional.empty(), MatchOutcome.MATCHED_NO_QUAY);
    }

    private enum MatchOutcome {
        MATCHED_QUAY, MATCHED_NO_QUAY, MATCHED_NO_TRACK, UNMATCHED
    }

    private record AssignmentResult(Optional<NeTExStopsData.NeTExStopAssignment> assignment,
            Optional<PetiQuay> quay, MatchOutcome outcome) {

        static AssignmentResult none() {
            return new AssignmentResult(Optional.empty(), Optional.empty(), MatchOutcome.UNMATCHED);
        }
    }

    /**
     * A station and track taken from the schedules. Blank tracks become null so that "", " "
     * and null do not end up as three separate copies of the same station-level stop point.
     */
    public record StationTrackPair(String stationShortCode, String commercialTrack) {
        public StationTrackPair {
            commercialTrack = StringUtils.isBlank(commercialTrack) ? null : commercialTrack;
        }
    }
}
