package dev.olegz.vf.common.exception;

import dev.olegz.vf.common.ApiResultCodes;

public class InvalidJwtException extends ApiResultException {

    public InvalidJwtException() {
        super(ApiResultCodes.WRONG_API_KEY, "Invalid JWT");
    }

    public InvalidJwtException(String apiErrorMessage) {
        super(ApiResultCodes.WRONG_API_KEY, apiErrorMessage);
    }
}
