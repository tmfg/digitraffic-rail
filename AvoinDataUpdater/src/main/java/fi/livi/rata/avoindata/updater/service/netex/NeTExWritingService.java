package fi.livi.rata.avoindata.updater.service.netex;

import java.time.ZonedDateTime;

/**
 * Serializes NeTEx domain objects to XML and produces the final ZIP output.
 * Uses Entur netex-java-model (JAXB) for marshalling.
 */
public class NeTExWritingService {

    /**
     * Assembles all NeTEx data into a PublicationDelivery XML document and writes it to a ZIP.
     *
     * @return ZIP file content as byte array
     */
    public byte[] writeNeTExZip(final NeTExStopsData stopsData,
                                 final NeTExRouteData routeData,
                                 final NeTExCalendarData calendarData,
                                 final java.util.List<NeTExEntityService.NeTExLine> lines,
                                 final java.util.List<NeTExEntityService.NeTExOperator> operators,
                                 final java.util.List<NeTExEntityService.NeTExServiceJourney> serviceJourneys,
                                 final ZonedDateTime generationTimestamp) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
