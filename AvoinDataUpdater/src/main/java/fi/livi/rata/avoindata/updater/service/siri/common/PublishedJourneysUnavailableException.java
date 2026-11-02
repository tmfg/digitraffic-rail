package fi.livi.rata.avoindata.updater.service.siri.common;

/**
 * Raised when a SIRI generation service (SIRI-ET, SIRI-VM) is configured to read published NeTEx journey refs
 * from the database but they are missing, cover none of the operating days, or are stale. Generation then fails
 * for the cycle rather than falling back to live resolution — the real-time feed must stay consistent with the
 * published NeTEx.
 */
public class PublishedJourneysUnavailableException extends RuntimeException {

    /**
     * Published journeys older than this (vs. their newest {@code generatedAt}) are stale. NeTEx is generated once
     * a day, so this is a day plus 6 hours of margin for a late or retried run.
     */
    public static final long MAX_AGE_HOURS = 30;

    /** Why the published NeTEx journeys could not be used. */
    public enum Reason {
        /** No dataset has ever been published (no rows at all). */
        MISSING,
        /** A dataset exists but has no journeys for the requested operating days. */
        EMPTY,
        /** The newest dataset is older than the freshness threshold. */
        STALE
    }

    private final transient Reason reason;

    public PublishedJourneysUnavailableException(final Reason reason, final String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
