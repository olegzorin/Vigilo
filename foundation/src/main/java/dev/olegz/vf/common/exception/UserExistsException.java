package dev.olegz.vf.common.exception;

import dev.olegz.vf.common.ApiResultCodes;

public class UserExistsException extends ApiResultException {
    public UserExistsException(String username, String altUsername) {
        super(ApiResultCodes.DUPLICATE_USERNAME, "User with login name '"
            + (username == null ? altUsername : username + (altUsername != null ? "/" + altUsername : ""))
            + "' exists already");
    }
}
