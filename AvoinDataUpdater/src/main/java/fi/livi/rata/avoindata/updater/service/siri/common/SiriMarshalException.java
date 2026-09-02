package fi.livi.rata.avoindata.updater.service.siri.common;

/** Thrown when building or serializing a SIRI document fails, so {@code error.type} names the real cause. */
public class SiriMarshalException extends RuntimeException {
    public SiriMarshalException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
