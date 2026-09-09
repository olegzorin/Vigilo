package dev.olegz.vf.common.exception;

import dev.olegz.vf.common.ApiResultCodes;

public class DuplicateEntityException extends ApiResultException {
    public DuplicateEntityException(String message) {
        super(ApiResultCodes.DUPLICATE_ENTITY, message);
    }
}
