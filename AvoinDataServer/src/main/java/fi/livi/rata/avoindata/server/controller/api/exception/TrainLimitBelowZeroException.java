package fi.livi.rata.avoindata.server.controller.api.exception;

import fi.livi.rata.avoindata.common.domain.common.ExceptionMessage;

public class TrainLimitBelowZeroException extends AbstractException {
    public TrainLimitBelowZeroException() {
        super("All parameters must be greater than or equal to 0");
    }

    @Override
    public ExceptionMessage.ErrorCodeEnum getCode() {
        return ExceptionMessage.ErrorCodeEnum.TRAIN_LIMIT_BELOW_ZERO;
    }
}
