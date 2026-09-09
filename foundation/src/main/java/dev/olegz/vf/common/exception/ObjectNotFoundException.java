package dev.olegz.vf.common.exception;

import dev.olegz.vf.common.ApiResultCodes;

public class ObjectNotFoundException extends ApiResultException {
    public ObjectNotFoundException(String message) {
        super(ApiResultCodes.OBJECT_NOT_FOUND, message);
    }
}
