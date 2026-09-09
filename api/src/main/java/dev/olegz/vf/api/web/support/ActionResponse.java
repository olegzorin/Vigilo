package dev.olegz.vf.api.web.support;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.olegz.vf.common.ApiResultCodes;

public class ActionResponse {
    public int resultCode;
    public String resultCodeMessage;
    public int collectionTotalSize;
    @JsonIgnore
    public boolean ordered;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getResultCodeDesc() {
        return ApiResultCodes.codeDescription(resultCode);
    }

	public void resultCodeAndMessage(byte resultCode, String resultCodeMessage) {
		this.resultCode = resultCode;
		this.resultCodeMessage = resultCodeMessage;
	}

}
