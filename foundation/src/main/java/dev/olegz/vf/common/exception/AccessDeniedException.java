package dev.olegz.vf.common.exception;

import dev.olegz.vf.common.ApiResultCodes;

public class AccessDeniedException extends ApiResultException {
    public AccessDeniedException(String apiErrorMessage) {
        super(ApiResultCodes.ACCESS_DENIED, apiErrorMessage);
    }
}
