package dev.olegz.vf.common.exception;

import dev.olegz.vf.common.ApiResultCodes;

public class ApiResultException extends RuntimeException {
    private final byte apiErrorCode;
    private final String apiErrorMessage;

    public ApiResultException(byte apiErrorCode, String apiErrorMessage) {
        super(apiErrorMessage);
        this.apiErrorCode = apiErrorCode;
        this.apiErrorMessage = apiErrorMessage;
    }

    public String getApiErrorMessage() {
    	return apiErrorMessage;
    }

    public byte getApiErrorCode() {
    	return apiErrorCode;
    }

    public boolean noWarn() {
        return ApiResultCodes.noWarn(apiErrorCode);
    }

    @Override
    public String toString() {
        return super.toString() + "\n  apiErrorCode=" + apiErrorCode + ", " + apiErrorMessage;
    }
}
