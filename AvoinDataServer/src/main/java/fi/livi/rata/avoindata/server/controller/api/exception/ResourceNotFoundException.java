package fi.livi.rata.avoindata.server.controller.api.exception;

import fi.livi.rata.avoindata.common.domain.common.ExceptionMessage;

public class ResourceNotFoundException extends AbstractException {
    
    protected ResourceNotFoundException(final String s) {
        super(s);
    }

    @Override
    public ExceptionMessage.ErrorCodeEnum getCode() {
        return ExceptionMessage.ErrorCodeEnum.RESOURCE_NOT_FOUND;
    }
}
