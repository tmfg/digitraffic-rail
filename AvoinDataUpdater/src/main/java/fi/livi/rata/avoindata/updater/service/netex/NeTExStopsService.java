package fi.livi.rata.avoindata.updater.service.netex;

import fi.livi.rata.avoindata.common.domain.metadata.Station;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * Maps station metadata to NeTEx ScheduledStopPoints, RoutePoints, and
 * DestinationDisplays.
 */
@Service
public class NeTExStopsService {

    private final NeTExIdGenerator idGenerator;

    public NeTExStopsService(final NeTExIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    /**
     * Creates NeTEx stop data from station metadata.
     * Only includes stations with passengerTraffic=true.
     */
    public NeTExStopsData createStopsData(final List<Station> stations) {
        final List<NeTExStopsData.NeTExScheduledStopPoint> stopPoints = new ArrayList<>();
        final List<NeTExStopsData.NeTExRoutePoint> routePoints = new ArrayList<>();
        final List<NeTExStopsData.NeTExDestinationDisplay> destinationDisplays = new ArrayList<>();

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
        }

        return new NeTExStopsData(stopPoints, routePoints, destinationDisplays);
    }
}
