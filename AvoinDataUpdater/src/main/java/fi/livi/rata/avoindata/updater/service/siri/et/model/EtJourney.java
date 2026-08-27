package fi.livi.rata.avoindata.updater.service.siri.et.model;

import java.util.List;

import fi.livi.rata.avoindata.updater.service.siri.common.DataFrameRef;
import fi.livi.rata.avoindata.updater.service.siri.common.LineId;
import fi.livi.rata.avoindata.updater.service.siri.common.OperatorRef;
import fi.livi.rata.avoindata.updater.service.siri.common.ServiceJourneyId;

/**
 * The interpreted real-time state of one planned journey, ready to marshal into an
 * {@code EstimatedVehicleJourney}. Purely our own vocabulary — no SIRI/JAXB types — so the interpretation
 * logic is testable without the marshaller.
 */
public record EtJourney(ServiceJourneyId serviceJourneyId, DataFrameRef dataFrameRef, LineId lineId,
                        OperatorRef operatorRef, boolean cancelled, boolean monitored, List<EtCall> calls) {}
