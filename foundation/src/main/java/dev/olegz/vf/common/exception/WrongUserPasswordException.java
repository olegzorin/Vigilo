package dev.olegz.vf.common.exception;

import dev.olegz.vf.common.ApiResultCodes;

public class WrongUserPasswordException extends ApiResultException {
    public WrongUserPasswordException() {
        super(ApiResultCodes.WRONG_USERNAME_PASSWORD, "Invalid username or password");
    }
}
