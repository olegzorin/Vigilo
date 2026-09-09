package dev.olegz.vf.common.exception;

import dev.olegz.vf.common.ApiResultCodes;

public class OperationNotAllowedException extends ApiResultException {
    public OperationNotAllowedException(String message) {
        super(ApiResultCodes.OPERATION_NOT_ALLOWED, message);
    }
}
