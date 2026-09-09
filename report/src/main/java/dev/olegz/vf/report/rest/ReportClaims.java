package dev.olegz.vf.report.rest;

import java.time.Instant;

import dev.olegz.vf.registry.service.encryption.JwtClaims;
import dev.olegz.vf.report.domain.ReportExecution;

/** One-day bearer credential for downloading a single report execution. */
public class ReportClaims extends JwtClaims {
    static final long EXPIRATION_SECONDS = 86_400L;

    public String oid;

    public ReportClaims() {
    }

    public ReportClaims(ReportExecution execution) {
        ty = JwtClaims.TYPE_REPORT;
        exp = Instant.now().getEpochSecond() + EXPIRATION_SECONDS;
        oid = execution.objectId;
    }

    @Override
    public boolean valid() {
        return (ty == JwtClaims.TYPE_REPORT) && (oid != null) && !oid.isBlank();
    }

    @Override
    public String toString() {
        return "{objectId=" + oid + '}';
    }
}
