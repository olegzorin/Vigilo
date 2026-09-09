package dev.olegz.vf.common.exception;

import dev.olegz.vf.common.ApiResultCodes;

public class ExternalException extends ApiResultException {

    public ExternalException(String message) {
        super(ApiResultCodes.EXTERNAL_APPLICATION_ERROR, message);
    }
}
