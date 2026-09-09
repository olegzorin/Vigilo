package dev.olegz.vf.common.exception;

import dev.olegz.vf.common.ApiResultCodes;

public class MissingParameterException extends ApiResultException {
    public MissingParameterException(String apiErrorMessage) {
        super(ApiResultCodes.MISSED_PARAMETER, apiErrorMessage);
    }
}
