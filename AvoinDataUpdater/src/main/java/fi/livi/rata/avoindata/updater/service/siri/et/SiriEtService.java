package fi.livi.rata.avoindata.updater.service.siri.et;

import java.time.ZonedDateTime;
import java.util.List;

import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriStopResolver;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriTimeConverter;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriWritingService;
import fi.livi.rata.avoindata.updater.service.siri.common.StopRef;
import fi.livi.rata.avoindata.updater.service.siri.et.model.EtCall;
import fi.livi.rata.avoindata.updater.service.siri.et.model.EtJourney;
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
        return buildEtDocumentWithStats(trains, now).document();
    }

    /** Builds the document and the per-cycle {@link SiriEtStats} that back the generation wide event. */
    public SiriEtResult buildEtDocumentWithStats(final List<GTFSTrain> trains, final ZonedDateTime now) {
        final Siri siri = siriWritingService.buildEnvelope(now, producerRef);

        final EstimatedTimetableDeliveryStructure delivery = new EstimatedTimetableDeliveryStructure();
        delivery.setVersion("2.0");
        delivery.setResponseTimestamp(now.withZoneSameInstant(SiriTimeConverter.HELSINKI_ZONE));

        final EstimatedVersionFrameStructure frame = new EstimatedVersionFrameStructure();
        frame.setRecordedAtTime(now.withZoneSameInstant(SiriTimeConverter.HELSINKI_ZONE));

        long emitted = 0;
        long cancelled = 0;
        long skippedUnresolvedJourney = 0;
        long skippedUnresolvedStop = 0;
        long callsRecorded = 0;
        long callsEstimated = 0;
        long stopRefsQuay = 0;
        long stopRefsStopPlace = 0;
        long stopRefsUnresolved = 0;

        for (final GTFSTrain train : trains) {
            switch (interpreter.interpret(train)) {
                case InterpretResult.Skipped skipped -> {
                    switch (skipped.reason()) {
                        case UNRESOLVED_JOURNEY -> skippedUnresolvedJourney++;
                        case UNRESOLVED_STOP -> {
                            skippedUnresolvedStop++;
                            stopRefsUnresolved++;
                        }
                    }
                }
                case InterpretResult.Emitted result -> {
                    final EtJourney journey = result.journey();
                    emitted++;
                    if (journey.cancelled()) {
                        cancelled++;
                    }
                    for (final EtCall call : journey.calls()) {
                        if (call instanceof EtCall.Recorded) {
                            callsRecorded++;
                        } else {
                            callsEstimated++;
                        }
                        if (isQuay(call.stopRef())) {
                            stopRefsQuay++;
                        } else {
                            stopRefsStopPlace++;
                        }
                    }
                    frame.getEstimatedVehicleJourneies().add(marshaller.marshal(journey, now));
                }
            }
        }

        delivery.getEstimatedJourneyVersionFrames().add(frame);
        siri.getServiceDelivery().getEstimatedTimetableDeliveries().add(delivery);

        final SiriEtStats stats = new SiriEtStats(emitted, cancelled, skippedUnresolvedJourney,
                skippedUnresolvedStop, callsRecorded, callsEstimated, stopRefsQuay, stopRefsStopPlace,
                stopRefsUnresolved);
        return new SiriEtResult(siri, stats);
    }

    // FSR:Quay:... when the track resolved, FSR:StopPlace:... on the track-unknown fallback.
    private static boolean isQuay(final StopRef stopRef) {
        return stopRef.value().contains(":Quay:");
    }
}
