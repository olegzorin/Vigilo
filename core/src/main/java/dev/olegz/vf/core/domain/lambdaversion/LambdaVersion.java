package dev.olegz.vf.core.domain.lambdaversion;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.core.domain.lambdaassignment.LambdaStatistics;
import dev.olegz.vf.core.domain.lambdabuild.LambdaCodeUpload;
import dev.olegz.vf.core.domain.lambdabuild.LambdaConfig;
import dev.olegz.vf.core.domain.lambdabuild.InvocationLaneSettings;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;

public class LambdaVersion {

    public static final byte LATEST_FALSE = 0; // Only allowed for the production version (status = 2)
    public static final byte LATEST_TRUE = 1;  // Allowed for the developer version in status 1 and the production version
    public static final byte LATEST_NONE = 2;  // Only allowed for the developer version in status 0, 3

    public int lambdaId;
    public int lambdaVersionId;
    public byte latest = LATEST_NONE;
    public boolean published;
    public Map<String, String> schedule;
    public Datetime scheduleStartDate;
    public LambdaVersionStatus status;
    public String version;
    /**
     * Internal, per-lambda sequential number reflecting the order in which this lambda's versions were
     * created (1, 2, 3, ...). Assigned once at creation from {@link Lambda#maxSequence} and never
     * changed thereafter, so it is monotonic and gap-free even as the semantic {@link #version} is
     * recomputed on re-save or rolled back. Used only for internal ordering; not exposed by the API.
     */
    public int sequenceNumber;
    public Datetime createdAt;
    public Integer codeUploadId;
    public String whatsnew;
    public Datetime statusDate;
    public int trigger;
    public String functionName;
    public String asyncFunctionName;
    public Timestamp functionStartDate;
    public byte arch;
    public int memory;
    public int timeout; // in seconds
    public List<String> addresses;
    public int createdBy;

    public LambdaStatistics statistics;

    public boolean isRunnable() {
        return status.isRunnable();
    }

    public boolean canUpdate() {
        return status.isUpdatable();
    }

    public void updateConfiguration(LambdaConfig newConfig) {
        this.trigger = newConfig.trigger;
        this.whatsnew = newConfig.whatsnew;
        this.addresses = newConfig.addresses;
        if (newConfig.schedule == null) {
            this.schedule = null;
            this.scheduleStartDate = null;
        } else if (!newConfig.schedule.equals(this.schedule)) {
            this.schedule = newConfig.schedule;
            this.scheduleStartDate = Datetime.now();
        }
    }

    public void updateFromLambdaCodeUpload(LambdaCodeUpload upload) {
        this.codeUploadId = upload.uploadId;
        this.arch = upload.arch;
        // Due to a bug, memory and timeout are zero for uploads saved in the database
        // in some time range. The bug only affects the rolled back public versions!
        InvocationLaneSettings defaultSettings = InvocationLaneSettings.forLane(InvocationLane.DEFAULT);
        this.memory = upload.memory > 0 ? upload.memory : defaultSettings.memorySize();
        this.timeout = upload.timeout > 0 ? upload.timeout : defaultSettings.timeout();
        for (InvocationLane lane : InvocationLane.values()) {
            setFunctionName(lane, upload.getFunctionName(lane));
        }
        this.functionStartDate = Timestamp.from(Instant.now().truncatedTo(ChronoUnit.SECONDS));
    }

    public String getFunctionName(InvocationLane lane) {
        return lane == InvocationLane.ASYNC ? asyncFunctionName : functionName;
    }

    public void setFunctionName(InvocationLane lane, String functionName) {
        if (lane == InvocationLane.ASYNC) {
            this.asyncFunctionName = functionName;
        } else {
            this.functionName = functionName;
        }
    }

    public void updateStatus(LambdaVersionStatus status) {
        if (this.status == status) return;
        this.status = status;
        this.statusDate = Datetime.now();
        if (status == LambdaVersionStatus.PRODUCTION) {
            scheduleStartDate = this.statusDate;
        } else if (status == LambdaVersionStatus.DISCARDED) {
            for (InvocationLane lane : InvocationLane.values()) setFunctionName(lane, null);
            functionStartDate = null;
            codeUploadId = null;
            arch = 0;
        }
    }

    public String checkRunnable() {
        if (!status.isRunnable()) return null;
        for (InvocationLane lane : InvocationLane.values()) {
            if (getFunctionName(lane) == null) {
                return lane == InvocationLane.ASYNC ? "async function name is missing" : "function name is missing";
            }
        }
        return functionStartDate == null ? "function start date is missing"
                : memory == 0 ? "memory is 0"
                  : timeout == 0 ? "timeout is 0"
                    : null;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder(250);
        s.append("{lambdaVersionId=").append(lambdaVersionId);
        s.append(", sequenceNumber=").append(sequenceNumber);
        s.append(", createdAt=").append(createdAt);
        s.append(", lambdaId=").append(lambdaId);
        s.append(", status=").append(status);
        s.append(", statusDate=").append(statusDate);
        s.append(", latest=").append(latest);
        s.append(", published=").append(published);

        if (codeUploadId != null) s.append(", codeUploadId=").append(codeUploadId);
        if (functionName != null) s.append(", functionName=").append(functionName);
        if (whatsnew != null) s.append(", whatsnew=").append(whatsnew);
        if (version != null) s.append(", version=").append(version);

        if (arch > 0) s.append(", arch=").append(arch);
        if (timeout > 0) s.append(", timeout=").append(timeout);
        if (memory > 0) s.append(", memory=").append(memory);

        if (addresses != null && !addresses.isEmpty()) s.append(",\naddresses=").append(addresses);

        return s.append('}').toString();
    }

    @Override
    public int hashCode() {
        return lambdaVersionId;
    }

}
