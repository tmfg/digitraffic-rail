package fi.livi.rata.avoindata.updater.service.netex;

import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiQuay;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStop;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiUicMatcher;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

/**
 * Maps station metadata to NeTEx ScheduledStopPoints, RoutePoints, and
 * DestinationDisplays. Wires PETI stop data to produce PassengerStopAssignments.
 */
@Service
public class NeTExStopsService {

    private final NeTExIdGenerator idGenerator;
    private final PetiStopSource petiStopSource;

    public NeTExStopsService(final NeTExIdGenerator idGenerator, final PetiStopSource petiStopSource) {
        this.idGenerator = idGenerator;
        this.petiStopSource = petiStopSource;
    }

    /**
     * Track-aware overload: creates NeTEx stop data with track-qualified SSPs.
     * Accepts (station, track) pairs derived from schedule data. Each unique pair produces
     * a track-qualified ScheduledStopPoint (DT:ScheduledStopPoint:{shortCode}-{track})
     * and, when PETI matches, a PassengerStopAssignment with StopPlaceRef and QuayRef.
     *
     * @param stations station metadata (for coordinates, names)
     * @param stationTrackPairs list of (stationShortCode, commercialTrack) tuples from schedules
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

        for (final StationTrackPair pair : uniquePairs) {
            final Station station = stationByShortCode.get(pair.stationShortCode());
            if (station == null) {
                continue;
            }

            stopPoints.add(buildScheduledStopPoint(pair, station));
            addStationLevelArtifacts(station, seenStations, routePoints, destinationDisplays);

            if (!petiSourceEmpty) {
                final AssignmentResult result = buildAssignment(pair, station, matcher);
                result.assignment().ifPresent(stopAssignments::add);
                switch (result.outcome()) {
                    case MATCHED_QUAY -> {
                        matchedCount++;
                        quayMatchedCount++;
                    }
                    case MATCHED_NO_QUAY -> {
                        matchedCount++;
                        quayUnmatchedCount++;
                    }
                    case MATCHED_NO_TRACK -> {
                        matchedCount++;
                        quayNoTrackCount++;
                    }
                    case UNMATCHED -> unmatchedCount++;
                }
            }
        }

        return new NeTExStopsData(stopPoints, routePoints, destinationDisplays,
                stopAssignments, matchedCount, unmatchedCount,
                quayMatchedCount, quayUnmatchedCount, quayNoTrackCount);
    }

    private NeTExStopsData.NeTExScheduledStopPoint buildScheduledStopPoint(final StationTrackPair pair,
            final Station station) {
        final String sspId = idGenerator.scheduledStopPointId(pair.stationShortCode(), pair.commercialTrack());
        return new NeTExStopsData.NeTExScheduledStopPoint(
                sspId, station.name, station.shortCode, station.latitude, station.longitude);
    }

    private void addStationLevelArtifacts(final Station station, final Set<String> seenStations,
            final List<NeTExStopsData.NeTExRoutePoint> routePoints,
            final List<NeTExStopsData.NeTExDestinationDisplay> destinationDisplays) {
        if (seenStations.add(station.shortCode)) {
            routePoints.add(new NeTExStopsData.NeTExRoutePoint(
                    idGenerator.routePointId(station.shortCode), station.shortCode));
            destinationDisplays.add(new NeTExStopsData.NeTExDestinationDisplay(
                    idGenerator.destinationDisplayId(station.shortCode), station.name));
        }
    }

    private AssignmentResult buildAssignment(final StationTrackPair pair, final Station station,
            final PetiUicMatcher matcher) {
        final Optional<PetiStop> petiMatch = matcher.match(station.uicCode);
        if (petiMatch.isEmpty()) {
            return new AssignmentResult(Optional.empty(), MatchOutcome.UNMATCHED);
        }

        final PetiStop matched = petiMatch.get();
        final String track = pair.commercialTrack();
        final String sspId = idGenerator.scheduledStopPointId(pair.stationShortCode(), track);
        final String assignmentId = idGenerator.passengerStopAssignmentId(pair.stationShortCode(), track);

        if (track == null || track.isBlank()) {
            return new AssignmentResult(
                    Optional.of(new NeTExStopsData.NeTExStopAssignment(
                            assignmentId, sspId, matched.stopPlaceId(), null)),
                    MatchOutcome.MATCHED_NO_TRACK);
        }

        final Optional<PetiQuay> quay = matched.resolveQuay(track);
        if (quay.isPresent()) {
            return new AssignmentResult(
                    Optional.of(new NeTExStopsData.NeTExStopAssignment(
                            assignmentId, sspId, matched.stopPlaceId(), quay.get().quayId())),
                    MatchOutcome.MATCHED_QUAY);
        }

        return new AssignmentResult(
                Optional.of(new NeTExStopsData.NeTExStopAssignment(
                        assignmentId, sspId, matched.stopPlaceId(), null)),
                MatchOutcome.MATCHED_NO_QUAY);
    }

    private enum MatchOutcome { MATCHED_QUAY, MATCHED_NO_QUAY, MATCHED_NO_TRACK, UNMATCHED }

    private record AssignmentResult(Optional<NeTExStopsData.NeTExStopAssignment> assignment, MatchOutcome outcome) {
    }

    /**
     * A (station, track) pair extracted from schedule data.
     */
    public record StationTrackPair(String stationShortCode, String commercialTrack) {
    }
}
