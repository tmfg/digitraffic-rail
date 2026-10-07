package fi.livi.rata.avoindata.updater.service.siri.common;

import java.time.LocalDate;
import java.util.List;

import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTimeTableRow;

/**
 * On-demand lookup of a train's full {@link GTFSTimeTableRow} list, keyed by (trainNumber, departureDate). Unlike
 * SIRI-ET (which already holds every row for a train it processes), SIRI-VM's live position query loads only a
 * single upcoming-stop row per train; this lookup is used to fetch the full row list only when that single row's
 * track is unknown, so {@link CommercialStopVisits} can resolve the station's current visit index for the planned
 * -track fallback (see {@code VmJourneyConverter}). Left as a seam so the generation service supplies the
 * implementation (batched up front for the whole cycle - see {@code SiriVmGenerationService.prepareContext}) and
 * the converter stays testable without one.
 */
@FunctionalInterface
public interface TimeTableRowsLookup {

    List<GTFSTimeTableRow> rowsFor(long trainNumber, LocalDate departureDate);
}
