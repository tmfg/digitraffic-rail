package fi.livi.rata.avoindata.updater.service.siri.et;

import java.time.ZonedDateTime;
import java.util.List;

import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriStopResolver;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriTimeConverter;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriWritingService;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.Siri;

/**
 * Builds a SIRI-ET {@code ServiceDelivery} document from live {@link GTFSTrain}s. This class owns only the
 * delivery envelope; per-journey work is split into two collaborators:
 * <ul>
 *   <li>{@link EtJourneyInterpreter} — decides <em>what the real-time situation is</em> and produces the
 *       {@code EtJourney} domain IR;</li>
 *   <li>{@link EtJourneyMarshaller} — mechanically maps that IR onto the SIRI {@code EstimatedVehicleJourney}.</li>
 * </ul>
 */
public class SiriEtService {

    private final SiriWritingService siriWritingService;
    private final String producerRef;
    private final EtJourneyInterpreter interpreter;
    private final EtJourneyMarshaller marshaller;

    public SiriEtService(
            final JourneyRefResolver journeyRefResolver,
            final StationUicLookup stationUicLookup,
            final SiriStopResolver siriStopResolver,
            final StationNameLookup stationNameLookup,
            final PlannedTrackLookup plannedTrackLookup,
            final SiriWritingService siriWritingService,
            final String producerRef,
            final String dataSource) {
        this.siriWritingService = siriWritingService;
        this.producerRef = producerRef;
        this.interpreter = new EtJourneyInterpreter(journeyRefResolver, stationUicLookup, siriStopResolver,
                stationNameLookup, plannedTrackLookup);
        this.marshaller = new EtJourneyMarshaller(dataSource);
    }

    public Siri buildEtDocument(final List<GTFSTrain> trains, final ZonedDateTime now) {
        final Siri siri = siriWritingService.buildEnvelope(now, producerRef);

        final EstimatedTimetableDeliveryStructure delivery = new EstimatedTimetableDeliveryStructure();
        delivery.setVersion("2.0");
        delivery.setResponseTimestamp(now.withZoneSameInstant(SiriTimeConverter.HELSINKI_ZONE));

        final EstimatedVersionFrameStructure frame = new EstimatedVersionFrameStructure();
        frame.setRecordedAtTime(now.withZoneSameInstant(SiriTimeConverter.HELSINKI_ZONE));

        for (final GTFSTrain train : trains) {
            interpreter.interpret(train)
                    .map(journey -> marshaller.marshal(journey, now))
                    .ifPresent(evj -> frame.getEstimatedVehicleJourneies().add(evj));
        }

        delivery.getEstimatedJourneyVersionFrames().add(frame);
        siri.getServiceDelivery().getEstimatedTimetableDeliveries().add(delivery);
        return siri;
    }
}
