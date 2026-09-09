package dev.olegz.vf.report;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ReportTestPaths {
    private ReportTestPaths() {
    }

    public static Path definitions() {
        Path fromRoot = Path.of("report/database/definitions");
        return Files.isDirectory(fromRoot) ? fromRoot : Path.of("database/definitions");
    }
}
