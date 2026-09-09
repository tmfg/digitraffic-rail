package fi.livi.rata.avoindata.updater.service.siri.et;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriStopResolver;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriWritingService;
import fi.livi.rata.avoindata.updater.service.siri.et.model.EtJourney;
import uk.org.siri.siri21.Siri;

/**
 * Builds a SIRI-ET {@code ServiceDelivery} document from live {@link GTFSTrain}s by running two phases over
 * the trains:
 * <ul>
 *   <li>{@link EtJourneyInterpreter} — decides <em>what the real-time situation is</em> and produces the
 *       {@code EtJourney} domain IR;</li>
 *   <li>{@link EtJourneyMarshaller} — maps that IR onto the SIRI {@code EstimatedVehicleJourney}s, assembles
 *       the delivery envelope and serializes it.</li>
 * </ul>
 * The per-cycle {@link SiriEtStats} are folded from the same interpretation results by {@link SiriEtStats#from}.
 */
public class SiriEtService {

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
        this.interpreter = new EtJourneyInterpreter(journeyRefResolver, stationUicLookup, siriStopResolver,
                stationNameLookup, plannedTrackLookup);
        this.marshaller = new EtJourneyMarshaller(siriWritingService, producerRef, dataSource);
    }

    public Siri buildEtDocument(final List<GTFSTrain> trains, final ZonedDateTime now) {
        return marshaller.marshal(emitted(interpret(trains, now)), now);
    }

    /** Builds the document + serialized bytes and the per-cycle {@link SiriEtStats} that back the wide event. */
    public SiriEtResult buildEtDocumentWithStats(final List<GTFSTrain> trains, final ZonedDateTime now) {
        final List<InterpretResult> results = interpret(trains, now);
        final SiriEtStats stats = SiriEtStats.from(results);
        final Siri document = marshaller.marshal(emitted(results), now);
        return new SiriEtResult(document, marshaller.marshalToBytes(document), stats);
    }

    private List<InterpretResult> interpret(final List<GTFSTrain> trains, final ZonedDateTime now) {
        final List<InterpretResult> results = new ArrayList<>(trains.size());
        for (final GTFSTrain train : trains) {
            results.add(interpreter.interpret(train, now));
        }
        return results;
    }

    private static List<EtJourney> emitted(final List<InterpretResult> results) {
        final List<EtJourney> journeys = new ArrayList<>();
        for (final InterpretResult result : results) {
            if (result instanceof InterpretResult.Emitted emittedResult) {
                journeys.add(emittedResult.journey());
            }
        }
        return journeys;
    }
}
