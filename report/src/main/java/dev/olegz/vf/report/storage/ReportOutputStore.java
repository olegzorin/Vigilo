package dev.olegz.vf.report.storage;

/** External storage for report ZIPs that do not fit in execution-history BLOBs. */
public interface ReportOutputStore {
    byte[] get(String objectId);

    void put(String objectId, byte[] data);
}
