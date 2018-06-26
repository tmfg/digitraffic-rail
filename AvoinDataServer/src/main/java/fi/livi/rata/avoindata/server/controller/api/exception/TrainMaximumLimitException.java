package fi.livi.rata.avoindata.server.controller.api.exception;

import fi.livi.rata.avoindata.common.domain.common.ExceptionMessage;

public class TrainMaximumLimitException extends AbstractException {
    public TrainMaximumLimitException(int maxTrainRetrieveRequest) {
        super("The number trains wanted is over the max size " + maxTrainRetrieveRequest);
    }

    @Override
    public ExceptionMessage.ErrorCodeEnum getCode() {
        return ExceptionMessage.ErrorCodeEnum.TRAIN_MAXIMUM_LIMIT_EXCEEDED;
    }
}
