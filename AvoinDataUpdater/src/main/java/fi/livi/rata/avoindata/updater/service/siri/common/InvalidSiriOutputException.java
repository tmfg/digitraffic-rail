package fi.livi.rata.avoindata.updater.service.siri.common;

/** Thrown when a generated SIRI document fails structural validation, to stop it being published. */
public class InvalidSiriOutputException extends RuntimeException {
    public InvalidSiriOutputException(final String message) {
        super(message);
    }
}
