package fi.livi.rata.avoindata.server.controller.api.exception;

import fi.livi.rata.avoindata.common.domain.common.ExceptionMessage;

/**
 * Luotu: 7.6.2016 klo 12:21
 *
 * @author jaakkopa
 */
public class VersionTooOldException extends AbstractException {
    public VersionTooOldException(Long maxVersion, Long requestedVersion) {
        super(String.format("Requested version %s is too old. Max version: %s", requestedVersion, maxVersion));
    }

    @Override
    public ExceptionMessage.ErrorCodeEnum getCode() {
        return ExceptionMessage.ErrorCodeEnum.VERSION_TOO_OLD;
    }
}
