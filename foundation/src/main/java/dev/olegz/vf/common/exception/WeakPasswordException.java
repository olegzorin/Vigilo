package dev.olegz.vf.common.exception;

import dev.olegz.vf.common.ApiResultCodes;

public class WeakPasswordException extends ApiResultException {
    public WeakPasswordException() {
        super(ApiResultCodes.WEAK_PASSWORD, "Weak password");
    }
}
