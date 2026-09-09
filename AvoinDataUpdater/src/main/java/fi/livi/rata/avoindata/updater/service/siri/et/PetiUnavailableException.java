package fi.livi.rata.avoindata.updater.service.siri.et;

/**
 * Raised when the PETI stop snapshot is unavailable (empty) after a warm-up attempt, so no commercial stop could
 * be resolved to a {@code FSR:Quay}. SIRI-ET generation then fails the cycle at the prepare stage — with a clear
 * {@code PETI_EMPTY} reason on the wide event — rather than building a feed that would resolve nothing (which the
 * SIRI 2.0 validation would in any case reject as an empty delivery). Keeps the last-good feed serving until the
 * PETI snapshot is loaded.
 */
public class PetiUnavailableException extends RuntimeException {

    /** The {@code unavailable_reason} value emitted on the generation wide event. */
    public static final String REASON = "PETI_EMPTY";

    public PetiUnavailableException(final String message) {
        super(message);
    }
}
