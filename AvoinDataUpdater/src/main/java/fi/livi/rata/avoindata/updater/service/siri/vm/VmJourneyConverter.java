package fi.livi.rata.avoindata.updater.service.siri.vm;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.apache.commons.lang3.BooleanUtils;

import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTimeTableRow;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrainLocation;
import fi.livi.rata.avoindata.updater.service.siri.common.CommercialStopVisits;
import fi.livi.rata.avoindata.updater.service.siri.common.JourneyEndpoint;
import fi.livi.rata.avoindata.updater.service.siri.common.ResolvedJourney;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriStopResolver;
import fi.livi.rata.avoindata.updater.service.siri.common.StopRef;
import fi.livi.rata.avoindata.updater.service.siri.common.TimeTableRowsLookup;
import fi.livi.rata.avoindata.updater.service.siri.et.JourneyRefResolver;
import fi.livi.rata.avoindata.updater.service.siri.et.PlannedTrackLookup;
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
    private final PlannedTrackLookup plannedTrackLookup;
    private final TimeTableRowsLookup timeTableRowsLookup;

    public VmJourneyConverter(final JourneyRefResolver journeyRefResolver,
                                 final StationUicLookup stationUicLookup,
                                 final SiriStopResolver siriStopResolver,
                                 final StationNameLookup stationNameLookup,
                                 final PlannedTrackLookup plannedTrackLookup,
                                 final TimeTableRowsLookup timeTableRowsLookup) {
        this.journeyRefResolver = journeyRefResolver;
        this.stationUicLookup = stationUicLookup;
        this.siriStopResolver = siriStopResolver;
        this.stationNameLookup = stationNameLookup;
        this.plannedTrackLookup = plannedTrackLookup;
        this.timeTableRowsLookup = timeTableRowsLookup;
    }

    public Optional<VmActivity> convert(final GTFSTrainLocation location, final ZonedDateTime now) {
        final Optional<ResolvedJourney> resolvedJourney =
                journeyRefResolver.resolve(location.getTrainNumber(), location.getDepartureDate());
        if (resolvedJourney.isEmpty()) {
            return Optional.empty();
        }

        final MonitoredCall monitoredCall = resolveMonitoredCall(location, now);
        final ResolvedEndpoint origin = resolveEndpoint(resolvedJourney.get().origin());
        final ResolvedEndpoint destination = resolveEndpoint(resolvedJourney.get().destination());
        // getSpeed() is km/h; SIRI/GTFS-Realtime both report vehicle speed in m/s.
        final double speedMetersPerSecond = location.getSpeed() / 3.6;

        return Optional.of(new VmActivity(location.getTrainNumber(), resolvedJourney.get(), location.getTimestamp(),
                location.getX(), location.getY(), speedMetersPerSecond,
                monitoredCall.stopRef(), monitoredCall.stopName(), location.getDelaySeconds(),
                origin.stopRef(), origin.stopName(), destination.stopRef(), destination.stopName(),
                location.getVehicleAtStop(), location.getUnknownDelay()));
    }

    /**
     * Resolves the upcoming stop (station + track already picked by the query that loaded the location — see
     * {@code GTFSTrainRepository.getTrainLocations}) to a PETI quay and its name. Either or both may come back
     * {@code null} when the station/track/quay cannot be resolved — a {@code MonitoredCall} is optional in the
     * Nordic profile, unlike SIRI-ET's complete stop sequence.
     */
    private MonitoredCall resolveMonitoredCall(final GTFSTrainLocation location, final ZonedDateTime now) {
        final String stationShortCode = location.getStationShortCode();
        if (stationShortCode == null) {
            return new MonitoredCall(null, null);
        }
        final OptionalInt uic = stationUicLookup.uicFor(stationShortCode);
        final String stopName = stationNameLookup.nameFor(stationShortCode).orElse(null);
        if (uic.isEmpty()) {
            return new MonitoredCall(null, stopName);
        }
        return new MonitoredCall(resolveMonitoredCallStopRef(location, stationShortCode, uic.getAsInt(), now), stopName);
    }

    /**
     * Resolves the upcoming stop's Quay from its live (real-time) track, falling back to the planned (NeTEx)
     * track when the live track is unknown ({@code unknownTrack=true}) — mirrors SIRI-ET's {@code
     * EtJourneyInterpreter.resolveStopRef} fallback: if the confirmed real-time track isn't known yet, the
     * planned value is used in its place. Unlike ET (which already holds the train's full row list while
     * converting), SIRI-VM only loads a single upcoming-stop row per train for its live position query, so it
     * has no visitIndex to pick the right planned track when a station is served more than once — that is
     * resolved here, on demand, by loading the train's full row list via {@link #timeTableRowsLookup}
     * <em>only</em> in this fallback path, so the common case (live track known) stays the original single
     * cheap query.
     */
    private StopRef resolveMonitoredCallStopRef(final GTFSTrainLocation location, final String stationShortCode,
                                                final int uic, final ZonedDateTime now) {
        final String actualTrack = actualTrackOf(location);
        if (actualTrack != null) {
            return siriStopResolver.resolveQuayId(uic, actualTrack).orElse(null);
        }
        final List<GTFSTimeTableRow> rows =
                timeTableRowsLookup.rowsFor(location.getTrainNumber(), location.getDepartureDate());
        final List<CommercialStopVisits.Stop> stops = CommercialStopVisits.of(rows);
        final OptionalInt visitIndex = CommercialStopVisits.currentVisitIndex(stops, stationShortCode, now);
        if (visitIndex.isEmpty()) {
            return null;
        }
        return plannedTrackLookup.plannedTrack(location.getTrainNumber(), location.getDepartureDate(),
                        stationShortCode, visitIndex.getAsInt())
                .flatMap(track -> siriStopResolver.resolveQuayId(uic, track))
                .orElse(null);
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
