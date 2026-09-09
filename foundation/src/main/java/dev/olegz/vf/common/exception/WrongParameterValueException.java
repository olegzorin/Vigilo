package dev.olegz.vf.common.exception;

import dev.olegz.vf.common.ApiResultCodes;

public class WrongParameterValueException extends ApiResultException {
    public WrongParameterValueException(String message) {
        super(ApiResultCodes.WRONG_PARAM_VALUE, message);
    }
}
