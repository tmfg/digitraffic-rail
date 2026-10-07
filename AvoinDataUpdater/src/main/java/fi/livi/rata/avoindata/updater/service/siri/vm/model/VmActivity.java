package fi.livi.rata.avoindata.updater.service.siri.vm.model;

import java.time.ZonedDateTime;

import fi.livi.rata.avoindata.updater.service.siri.common.ResolvedJourney;
import fi.livi.rata.avoindata.updater.service.siri.common.StopRef;
import fi.livi.rata.avoindata.updater.service.siri.vm.SiriVmService;
import fi.livi.rata.avoindata.updater.service.siri.vm.SiriVmStats;
import fi.livi.rata.avoindata.updater.service.siri.vm.VmJourneyConverter;

/// The domain IR for one SIRI-VM {@code VehicleActivity}: a single train's most recent reported position,
/// already resolved against the published NeTEx journey (for {@code LineRef}/{@code FramedVehicleJourneyRef})
/// and, when the upcoming stop's track is known, the PETI {@code FSR:Quay} it is approaching.
///
/// **Why this intermediate representation exists** (rather than {@link VmJourneyConverter} building SIRI
/// JAXB types directly): it keeps *conversion* (what the real-time situation is) and
/// *marshalling* (how it's expressed as XML) as separate, independently testable concerns — the same
/// separation SIRI-ET uses (see {@code EtCall}/{@code ResolvedJourney}). Concretely this buys three things:
/// (1) {@link VmJourneyConverter} unit tests assert on plain field values instead of walking a JAXB tree;
/// (2) {@link SiriVmService} can compute {@link SiriVmStats} (e.g. how many locations were dropped as
/// unresolvable) from the size of the {@code VmActivity} list, before any XML exists; (3) the converter never
/// needs to depend on {@code uk.org.siri.siri21} types, so a SIRI schema/version change only touches the
/// marshaller.
///
/// @param monitoredCallStopRef  the upcoming stop's resolved quay, or {@code null} when unresolved/unknown
/// @param monitoredCallStopName the upcoming stop's human-readable name, or {@code null} when unknown
/// @param delaySeconds          real-time delay against the upcoming stop's scheduled time (positive = late), or
///                              {@code null} when unknown. The Nordic profile expects SIRI-VM to carry both
///                              position *and* real-time delay (Handbook N801, 5.1).
/// @param originStopRef         the journey's first commercial stop's resolved quay (from the published NeTEx
///                              track, not live position), or {@code null} when unresolved/unknown.
/// @param originName            the origin stop's human-readable name, or {@code null} when unknown.
/// @param destinationStopRef    the journey's last commercial stop's resolved quay, or {@code null}.
/// @param destinationName       the destination stop's human-readable name, or {@code null} when unknown.
/// @param vehicleAtStop         whether the train is currently at (dwelling at, or not yet departed from) the
///                              {@code monitoredCallStopRef} stop, as opposed to still approaching it, or
///                              {@code null} when unknown (no {@code monitoredCallStopRef} resolved).
/// @param unknownDelay          whether the source system has flagged {@link #delaySeconds()} as unreliable
///                              (it cannot estimate how long the train will actually wait), or {@code null}/
///                              {@code false} in the normal case. Surfaced as SIRI-VM's {@code InCongestion} by
///                              {@link fi.livi.rata.avoindata.updater.service.siri.vm.VmJourneyMarshaller}.
public record VmActivity(
        long trainNumber,
        ResolvedJourney journey,
        ZonedDateTime recordedAtTime,
        double longitude,
        double latitude,
        double speedMetersPerSecond,
        StopRef monitoredCallStopRef,
        String monitoredCallStopName,
        Integer delaySeconds,
        StopRef originStopRef,
        String originName,
        StopRef destinationStopRef,
        String destinationName,
        Boolean vehicleAtStop,
        Boolean unknownDelay) {
}
