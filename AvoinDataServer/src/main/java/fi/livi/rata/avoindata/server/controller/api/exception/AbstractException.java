package fi.livi.rata.avoindata.server.controller.api.exception;

import fi.livi.rata.avoindata.common.domain.common.ExceptionMessage;

public abstract class AbstractException extends RuntimeException {
    protected AbstractException(String s) {
        super(s);
    }

    public abstract ExceptionMessage.ErrorCodeEnum getCode();
}
