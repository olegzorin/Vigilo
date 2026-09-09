package dev.olegz.vf.common.exception;

import dev.olegz.vf.common.ApiResultCodes;

public class ExternalConnectionException extends ApiResultException {
    public ExternalConnectionException(String message) {
        super(ApiResultCodes.EXTERNAL_CONNECTION_ERROR, message);
    }
}
