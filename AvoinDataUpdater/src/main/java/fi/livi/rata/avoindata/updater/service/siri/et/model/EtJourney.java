package fi.livi.rata.avoindata.updater.service.siri.et.model;

import java.util.List;

import fi.livi.rata.avoindata.updater.service.siri.common.DataFrameRef;
import fi.livi.rata.avoindata.updater.service.siri.common.JourneyPatternRef;
import fi.livi.rata.avoindata.updater.service.siri.common.LineId;
import fi.livi.rata.avoindata.updater.service.siri.common.OperatorRef;
import fi.livi.rata.avoindata.updater.service.siri.common.ServiceJourneyId;

/**
 * The interpreted real-time state of one planned journey, ready to marshal into an
 * {@code EstimatedVehicleJourney}. Purely our own vocabulary — no SIRI/JAXB types — so the interpretation
 * logic is testable without the marshaller.
 *
 * <p>{@code originName} / {@code destinationName} are the first/last commercial stop's station names (null
 * when unavailable) for the SIRI {@code OriginName} / {@code DestinationName} fields.
 */
public record EtJourney(ServiceJourneyId serviceJourneyId, DataFrameRef dataFrameRef, LineId lineId,
                        OperatorRef operatorRef, JourneyPatternRef journeyPatternRef,
                        boolean cancelled, boolean monitored,
                        String originName, String destinationName, List<EtCall> calls) {}
