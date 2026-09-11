package fi.livi.rata.avoindata.updater.service.siri.vm;

import java.util.Optional;
import java.util.OptionalInt;

import org.apache.commons.lang3.BooleanUtils;

import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrainLocation;
import fi.livi.rata.avoindata.updater.service.siri.common.JourneyEndpoint;
import fi.livi.rata.avoindata.updater.service.siri.common.ResolvedJourney;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriStopResolver;
import fi.livi.rata.avoindata.updater.service.siri.common.StopRef;
import fi.livi.rata.avoindata.updater.service.siri.et.JourneyRefResolver;
import fi.livi.rata.avoindata.updater.service.siri.et.StationNameLookup;
import fi.livi.rata.avoindata.updater.service.siri.et.StationUicLookup;
import fi.livi.rata.avoindata.updater.service.siri.vm.model.VmActivity;

/// Converts a live {@link GTFSTrainLocation} into the domain {@link VmActivity} IR: it decides <em>what the
/// real-time position situation is</em> (journey ref, upcoming-stop quay) without touching any SIRI/JAXB type.
/// Marshalling is a separate concern ({@link VmJourneyMarshaller}).
///
/// Reuses the same {@link JourneyRefResolver} / {@link StationUicLookup} / {@link SiriStopResolver} /
/// {@link StationNameLookup} collaborators as SIRI-ET, since both services resolve the same published-journey
/// and PETI-quay data — only the conversion differs.
///
/// Returns {@link Optional#empty()} when the location's train does not resolve to a published
/// {@code ServiceJourney}: the Nordic profile ties every real-time item to the plan, so an unresolvable
/// position is dropped rather than emitted with a synthetic journey reference.
public class VmJourneyConverter {

    private final JourneyRefResolver journeyRefResolver;
    private final StationUicLookup stationUicLookup;
    private final SiriStopResolver siriStopResolver;
    private final StationNameLookup stationNameLookup;

    public VmJourneyConverter(final JourneyRefResolver journeyRefResolver,
                                 final StationUicLookup stationUicLookup,
                                 final SiriStopResolver siriStopResolver,
                                 final StationNameLookup stationNameLookup) {
        this.journeyRefResolver = journeyRefResolver;
        this.stationUicLookup = stationUicLookup;
        this.siriStopResolver = siriStopResolver;
        this.stationNameLookup = stationNameLookup;
    }

    public Optional<VmActivity> convert(final GTFSTrainLocation location) {
        final Optional<ResolvedJourney> resolvedJourney =
                journeyRefResolver.resolve(location.getTrainNumber(), location.getDepartureDate());
        if (resolvedJourney.isEmpty()) {
            return Optional.empty();
        }

        final MonitoredCall monitoredCall = resolveMonitoredCall(location);
        final ResolvedEndpoint origin = resolveEndpoint(resolvedJourney.get().origin());
        final ResolvedEndpoint destination = resolveEndpoint(resolvedJourney.get().destination());
        // getSpeed() is km/h; SIRI/GTFS-Realtime both report vehicle speed in m/s.
        final double speedMetersPerSecond = location.getSpeed() / 3.6;

        return Optional.of(new VmActivity(location.getTrainNumber(), resolvedJourney.get(), location.getTimestamp(),
                location.getX(), location.getY(), speedMetersPerSecond,
                monitoredCall.stopRef(), monitoredCall.stopName(), location.getDelaySeconds(),
                origin.stopRef(), origin.stopName(), destination.stopRef(), destination.stopName(),
                location.getVehicleAtStop()));
    }

    /**
     * Resolves the upcoming stop (station + track already picked by the query that loaded the location — see
     * {@code GTFSTrainRepository.getTrainLocations}) to a PETI quay and its name. Either or both may come back
     * {@code null} when the station/track/quay cannot be resolved — a {@code MonitoredCall} is optional in the
     * Nordic profile, unlike SIRI-ET's complete stop sequence.
     */
    private MonitoredCall resolveMonitoredCall(final GTFSTrainLocation location) {
        final String stationShortCode = location.getStationShortCode();
        if (stationShortCode == null) {
            return new MonitoredCall(null, null);
        }
        final OptionalInt uic = stationUicLookup.uicFor(stationShortCode);
        final StopRef stopRef = uic.isPresent()
                ? siriStopResolver.resolveQuayId(uic.getAsInt(), actualTrackOf(location)).orElse(null)
                : null;
        final String stopName = stationNameLookup.nameFor(stationShortCode).orElse(null);
        return new MonitoredCall(stopRef, stopName);
    }

    /**
     * Resolves a journey endpoint (origin or destination, from the published NeTEx track — see
     * {@link ResolvedJourney#origin()}/{@link ResolvedJourney#destination()}) to a PETI quay and its name, for
     * the optional SIRI-VM {@code OriginRef}/{@code DestinationRef} fields. Unlike {@link #resolveMonitoredCall},
     * this uses the <em>planned</em> track (no live position exists for a stop the vehicle isn't currently
     * approaching). Returns nulls when the endpoint itself is unknown or its station/track doesn't resolve.
     */
    private ResolvedEndpoint resolveEndpoint(final JourneyEndpoint endpoint) {
        if (endpoint == null || endpoint.stationShortCode() == null) {
            return new ResolvedEndpoint(null, null);
        }
        final OptionalInt uic = stationUicLookup.uicFor(endpoint.stationShortCode());
        final StopRef stopRef = uic.isPresent()
                ? siriStopResolver.resolveQuayId(uic.getAsInt(), endpoint.plannedTrack()).orElse(null)
                : null;
        final String stopName = stationNameLookup.nameFor(endpoint.stationShortCode()).orElse(null);
        return new ResolvedEndpoint(stopRef, stopName);
    }

    private static String actualTrackOf(final GTFSTrainLocation location) {
        return BooleanUtils.isTrue(location.getUnknownTrack()) ? null : location.getCommercialTrack();
    }

    private record MonitoredCall(StopRef stopRef, String stopName) {}

    private record ResolvedEndpoint(StopRef stopRef, String stopName) {}
}
