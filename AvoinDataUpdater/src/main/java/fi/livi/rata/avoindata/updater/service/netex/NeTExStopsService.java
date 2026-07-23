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

            final String track = pair.commercialTrack();
            final String sspId = idGenerator.scheduledStopPointId(pair.stationShortCode(), track);

            stopPoints.add(new NeTExStopsData.NeTExScheduledStopPoint(
                    sspId, station.name, station.shortCode, station.latitude, station.longitude));

            if (seenStations.add(pair.stationShortCode())) {
                routePoints.add(new NeTExStopsData.NeTExRoutePoint(
                        idGenerator.routePointId(station.shortCode), station.shortCode));
                destinationDisplays.add(new NeTExStopsData.NeTExDestinationDisplay(
                        idGenerator.destinationDisplayId(station.shortCode), station.name));
            }

            if (!petiSourceEmpty) {
                final Optional<PetiStop> petiMatch = matcher.match(station.uicCode);
                if (petiMatch.isPresent()) {
                    matchedCount++;
                    final PetiStop matched = petiMatch.get();
                    final String assignmentId = idGenerator.passengerStopAssignmentId(pair.stationShortCode(), track);

                    if (track == null || track.isBlank()) {
                        quayNoTrackCount++;
                        stopAssignments.add(new NeTExStopsData.NeTExStopAssignment(
                                assignmentId, sspId, matched.stopPlaceId(), null));
                    } else {
                        final Optional<PetiQuay> quay = matched.resolveQuay(track);
                        if (quay.isPresent()) {
                            quayMatchedCount++;
                            stopAssignments.add(new NeTExStopsData.NeTExStopAssignment(
                                    assignmentId, sspId, matched.stopPlaceId(), quay.get().quayId()));
                        } else {
                            quayUnmatchedCount++;
                            stopAssignments.add(new NeTExStopsData.NeTExStopAssignment(
                                    assignmentId, sspId, matched.stopPlaceId(), null));
                        }
                    }
                } else {
                    unmatchedCount++;
                }
            }
        }

        return new NeTExStopsData(stopPoints, routePoints, destinationDisplays,
                stopAssignments, matchedCount, unmatchedCount,
                quayMatchedCount, quayUnmatchedCount, quayNoTrackCount);
    }

    /**
     * A (station, track) pair extracted from schedule data.
     */
    public record StationTrackPair(String stationShortCode, String commercialTrack) {
    }
}
