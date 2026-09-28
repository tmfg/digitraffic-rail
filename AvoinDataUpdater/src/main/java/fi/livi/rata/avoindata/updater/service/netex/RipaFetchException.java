package fi.livi.rata.avoindata.updater.service.netex;

/**
 * A generation run that failed while fetching schedules from RIPA. Marks the one failure worth
 * retrying: RIPA being briefly unavailable says nothing about our own data, so a later attempt has a
 * real chance of succeeding, unlike a failure in the deterministic build that follows.
 */
public class RipaFetchException extends RuntimeException {

    public RipaFetchException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
