package fi.livi.rata.avoindata.common.domain.common;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.Hidden;

@Hidden
public class ExceptionMessage implements Serializable {

    @JsonIgnore
    public String requestUrl;
    public String queryString;
    public ErrorCodeEnum code;
    public String errorMessage;

    public ExceptionMessage(final String errorMessage, final ErrorCodeEnum code, final String requestUrl, final String queryString) {
        this.errorMessage = errorMessage;
        this.code = code;
        this.requestUrl = requestUrl;
        this.queryString = queryString;
    }

    public enum ErrorCodeEnum {
        TOO_LONG_REQUESTED_TIME_PERIOD_FOR_SINGLE_QUERY,
        TRAIN_LIMIT_BELOW_ZERO,
        TRAIN_MAXIMUM_LIMIT_EXCEEDED,
        ACCESS_DENIED,
        PARAMETER_WRONG_TYPE,
        INTERNAL_ERROR,
        COMPOSITION_NOT_FOUND,
        TRAIN_NOT_FOUND,
        MISSING_MANDATORY_PARAMETER,
        END_DATE_IN_FUTURE_FOR_HISTORY_QUERY,
        TOO_MUCH_LOAD_IN_SYSTEM,
        TRAIN_MINIUM_LIMIT_ERROR,
        END_DATE_BEFORE_START_DATE,
        VERSION_TOO_OLD,
        UNKNOWN_PARAMETER,
        ILLEGAL_ARGUMENT_EXCEPTION,
        RESOURCE_NOT_FOUND
    }

    @Override
    public String toString() {
        return "ExceptionMessage{" +
                "requestUrl='" + requestUrl + '\'' +
                ", queryString='" + queryString + '\'' +
                ", code=" + code +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
