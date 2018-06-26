package fi.livi.rata.avoindata.server.controller.api.exception;

public abstract class AbstractNotFoundException extends AbstractException {
    protected AbstractNotFoundException(String s) {
        super(s);
    }
}
