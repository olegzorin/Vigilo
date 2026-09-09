package dev.olegz.vf.report.rest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.olegz.vf.common.ApiResultCodes;

public class ReportActionResponse {
    public int resultCode;
    public String resultCodeMessage;
    public int collectionTotalSize;
    @JsonIgnore
    public boolean ordered;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getResultCodeDesc() {
        return ApiResultCodes.codeDescription(resultCode);
    }
}
