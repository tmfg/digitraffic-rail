package fi.livi.rata.avoindata.updater.service.netex;

import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStop;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiUicMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
     * Creates NeTEx stop data from station metadata.
     * Only includes stations with passengerTraffic=true.
     * For each passenger station, attempts PETI UIC matching to produce a
     * PassengerStopAssignment linking the ScheduledStopPoint to a StopPlace.
     */
    public NeTExStopsData createStopsData(final List<Station> stations) {
        final List<NeTExStopsData.NeTExScheduledStopPoint> stopPoints = new ArrayList<>();
        final List<NeTExStopsData.NeTExRoutePoint> routePoints = new ArrayList<>();
        final List<NeTExStopsData.NeTExDestinationDisplay> destinationDisplays = new ArrayList<>();
        final List<NeTExStopsData.NeTExStopAssignment> stopAssignments = new ArrayList<>();

        final boolean petiSourceEmpty = petiStopSource.getStops().isEmpty();
        final PetiUicMatcher matcher = petiStopSource.getMatcher();
        int matchedCount = 0;
        int unmatchedCount = 0;

        for (final Station station : stations) {
            if (!station.passengerTraffic) {
                continue;
            }

            stopPoints.add(new NeTExStopsData.NeTExScheduledStopPoint(
                    idGenerator.scheduledStopPointId(station.shortCode),
                    station.name,
                    station.shortCode,
                    station.latitude,
                    station.longitude));

            routePoints.add(new NeTExStopsData.NeTExRoutePoint(
                    idGenerator.routePointId(station.shortCode),
                    station.shortCode));

            destinationDisplays.add(new NeTExStopsData.NeTExDestinationDisplay(
                    idGenerator.destinationDisplayId(station.shortCode),
                    station.name));

            if (!petiSourceEmpty) {
                final Optional<PetiStop> match = matcher.match(station.uicCode);
                if (match.isPresent()) {
                    matchedCount++;
                    stopAssignments.add(new NeTExStopsData.NeTExStopAssignment(
                            idGenerator.passengerStopAssignmentId(station.shortCode),
                            idGenerator.scheduledStopPointId(station.shortCode),
                            match.get().stopPlaceId()));
                } else {
                    unmatchedCount++;
                }
            }
        }

        return new NeTExStopsData(stopPoints, routePoints, destinationDisplays,
                stopAssignments, matchedCount, unmatchedCount);
    }
}
