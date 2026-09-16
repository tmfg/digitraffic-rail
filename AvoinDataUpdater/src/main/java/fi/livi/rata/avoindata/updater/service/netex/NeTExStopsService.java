package fi.livi.rata.avoindata.updater.service.netex;

import java.util.ArrayList;
import java.util.HashMap;
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

import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiQuay;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStop;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiUicMatcher;

/**
 * Maps station metadata to NeTEx ScheduledStopPoints, RoutePoints, and
 * DestinationDisplays. Wires PETI stop data to produce
 * PassengerStopAssignments.
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
     * Track-aware overload: creates NeTEx stop data with track-qualified SSPs.
     * Accepts (station, track) pairs derived from schedule data. Each unique pair
     * produces
     * a track-qualified ScheduledStopPoint
     * (DT:ScheduledStopPoint:{shortCode}-{track})
     * and, when PETI matches, a PassengerStopAssignment with StopPlaceRef and
     * QuayRef.
     *
     * @param stations          station metadata (for coordinates, names)
     * @param stationTrackPairs list of (stationShortCode, commercialTrack) tuples
     *                          from schedules
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
        final List<String> tracksWithoutQuay = new ArrayList<>();

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
                    case MATCHED_NO_QUAY -> {
                        matchedCount++;
                        quayUnmatchedCount++;
                        tracksWithoutQuay.add(pair.stationShortCode() + "-" + pair.commercialTrack());
                    }
                    case MATCHED_NO_TRACK -> {
                        matchedCount++;
                        quayNoTrackCount++;
                    }
                    case UNMATCHED -> unmatchedCount++;
                }
            }
        }

        for (final String shortCode : seenStations) {
            routePoints.add(new NeTExStopsData.NeTExRoutePoint(
                    idGenerator.routePointId(shortCode), shortCode, projectionTargets.get(shortCode)));
        }

        // One line listing every gap, because the per-track errors above are easy to lose in a long run.
        // Each needs a human decision: either PETI is missing a passenger platform, or the schedule put a
        // passenger train on a track that is not one.
        if (!tracksWithoutQuay.isEmpty()) {
            log.error("event=generateNeTEx method=createStopsData "
                    + "count={} tracks={} message=\"no PETI quay for these tracks, stops published without a "
                    + "QuayRef\"",
                    tracksWithoutQuay.size(), tracksWithoutQuay);
        }

        return new NeTExStopsData(stopPoints, routePoints, destinationDisplays,
                stopAssignments, matchedCount, unmatchedCount,
                quayMatchedCount, quayUnmatchedCount, quayNoTrackCount);
    }

    /**
     * Station names in the rail metadata carry an " asema" ("station") suffix that
     * passengers do not use. Anchored on whitespace so compounds such as
     * "Lentoasema" and "Pasila autojuna-asema" are left alone.
     */
    public static String publicStationName(final String stationName) {
        return stationName == null ? null : stationName.replaceAll("\\s+asema$", "");
    }

    /** No location: it resolves through the assignment's quay, or its stop place. */
    private NeTExStopsData.NeTExScheduledStopPoint buildScheduledStopPoint(final StationTrackPair pair,
            final Station station) {
        return new NeTExStopsData.NeTExScheduledStopPoint(
                idGenerator.scheduledStopPointId(pair.stationShortCode(), pair.commercialTrack()),
                publicStationName(station.name), station.shortCode);
    }

    /**
     * A RoutePoint is the station, so it carries the station centroid; the profile also
     * requires it to name a ScheduledStopPoint it corresponds to.
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
            // A matched station takes its first platform as a track upstream, so a track is only
            // blank here when the stop place publishes no quays at all — nothing to assign.
            return new AssignmentResult(
                    Optional.of(new NeTExStopsData.NeTExStopAssignment(
                            assignmentId, sspId, matched.stopPlaceId(), null)),
                    Optional.empty(),
                    MatchOutcome.MATCHED_NO_TRACK);
        }

        final Optional<PetiQuay> quay = matched.resolveQuay(track);
        if (quay.isPresent()) {
            return new AssignmentResult(
                    Optional.of(new NeTExStopsData.NeTExStopAssignment(
                            assignmentId, sspId, matched.stopPlaceId(), quay.get().quayId())),
                    quay,
                    MatchOutcome.MATCHED_QUAY);
        }

        // the schedule names a track PETI does not know, so the assignment loses its
        // quay and the stop is only locatable to the station
        log.error("event=generateNeTEx method=buildAssignment PETI quay not found for track station={} "
                + "uic={} track={} stopPlace={} stopPlaceName={} petiTracks={} assignment={}",
                pair.stationShortCode(), station.uicCode, track,
                matched.stopPlaceId(), matched.name(),
                matched.quays().stream().map(PetiQuay::publicCode).toList(),
                assignmentId);

        return new AssignmentResult(
                Optional.of(new NeTExStopsData.NeTExStopAssignment(
                        assignmentId, sspId, matched.stopPlaceId(), null)),
                Optional.empty(),
                MatchOutcome.MATCHED_NO_QUAY);
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
     * A (station, track) pair extracted from schedule data. A blank track is held as
     * null so that two pairs are equal exactly when they name the same stop point.
     */
    public record StationTrackPair(String stationShortCode, String commercialTrack) {
        public StationTrackPair {
            commercialTrack = StringUtils.isBlank(commercialTrack) ? null : commercialTrack;
        }
    }
}
