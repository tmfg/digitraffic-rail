package fi.livi.rata.avoindata.updater.service.siri.et;

/**
 * Raised when SIRI-ET is configured to read published NeTEx journey refs from the database but they are
 * missing, cover none of the operating days, or are stale. SIRI-ET generation then fails for the cycle rather
 * than falling back to live resolution — the real-time feed must stay consistent with the published NeTEx.
 */
public class PublishedJourneysUnavailableException extends RuntimeException {

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
