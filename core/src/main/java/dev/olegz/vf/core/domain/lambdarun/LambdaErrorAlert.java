package dev.olegz.vf.core.domain.lambdarun;

import java.sql.Timestamp;

public class LambdaErrorAlert {

    public String message;
    public Timestamp errorDate;
    public int errorCount;

    private LambdaErrorAlert() {
    }

    public LambdaErrorAlert(String message, Timestamp errorDate, int errorCount) {
        this.message = message;
        this.errorDate = errorDate;
        this.errorCount = errorCount;
    }
}
