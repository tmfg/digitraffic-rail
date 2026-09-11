package fi.livi.rata.avoindata.updater.service.siri.vm;

import java.time.ZonedDateTime;
import java.util.List;

import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrainLocation;
import fi.livi.rata.avoindata.updater.service.siri.vm.model.VmActivity;
import uk.org.siri.siri21.Siri;

/// Builds a SIRI-VM {@code ServiceDelivery} document from live {@link GTFSTrainLocation}s by running two phases
/// over the locations:
/// - {@link VmJourneyConverter} — resolves each location to a published journey (and, when possible, the
///   upcoming stop's PETI quay), producing the {@code VmActivity} domain IR;
/// - {@link VmJourneyMarshaller} — maps that IR onto the SIRI {@code VehicleActivity}s, assembles the
///   delivery envelope and serializes it.
public class SiriVmService {

    private final VmJourneyConverter converter;
    private final VmJourneyMarshaller marshaller;

    public SiriVmService(final VmJourneyConverter converter, final VmJourneyMarshaller marshaller) {
        this.converter = converter;
        this.marshaller = marshaller;
    }

    /// Builds the document + serialized bytes and the per-cycle {@link SiriVmStats} that back the wide event.
    public SiriVmResult buildVmDocumentWithStats(final List<GTFSTrainLocation> locations, final ZonedDateTime now) {
        final List<VmActivity> activities = convertLocationsToActivities(locations);
        final SiriVmStats stats = new SiriVmStats(locations.size(), activities.size());
        final Siri document = marshaller.marshal(activities, now);
        return new SiriVmResult(document, marshaller.marshalToBytes(document), stats);
    }

    private List<VmActivity> convertLocationsToActivities(final List<GTFSTrainLocation> locations) {
        return locations.stream()
            .flatMap(location -> converter.convert(location).stream())
            .toList();
    }
}
