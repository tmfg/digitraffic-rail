package fi.livi.rata.avoindata.server.controller.api.exception;

import fi.livi.rata.avoindata.common.domain.common.ExceptionMessage;

public class TrainMinimumLimitException extends AbstractException {
    public TrainMinimumLimitException() {
        super("Total number of trains requested must be greater than zero.");
    }

    @Override
    public ExceptionMessage.ErrorCodeEnum getCode() {
        return ExceptionMessage.ErrorCodeEnum.TRAIN_MINIUM_LIMIT_ERROR;
    }
}
