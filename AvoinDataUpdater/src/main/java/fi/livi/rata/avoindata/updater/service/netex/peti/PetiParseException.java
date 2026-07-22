package fi.livi.rata.avoindata.updater.service.netex.peti;

/**
 * Thrown when a PETI NeTEx stops.xml document cannot be parsed at all — e.g. malformed, truncated,
 * or otherwise structurally invalid XML.
 *
 * <p>This is a document-level failure and is deliberately distinct from an individual StopPlace being
 * skipped (a StopPlace with a missing or non-numeric uicCode is skipped and counted, per TICKET-01 §8).
 * By failing fast here, the caller (the PETI fetch/refresh step) can keep the last-good cached snapshot
 * instead of overwriting it with partial or empty data, preventing a mixed/degraded state downstream.
 */
public class PetiParseException extends RuntimeException {

    public PetiParseException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
