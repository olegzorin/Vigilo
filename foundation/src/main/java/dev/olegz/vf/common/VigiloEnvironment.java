package dev.olegz.vf.common;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Access to Vigilo Framework environment settings and derived filesystem locations.
 */
public final class VigiloEnvironment {
    private static final String KEK_PROPERTY = "VF_KEK";
    private static final String HOME_PROPERTY = "VF_HOME";
    private static final String LOG_HOME_PROPERTY = "VF_LOG_HOME";

    private static final String CONFIG_DIR = "config";
    private static final String PROPERTIES_DIR = "properties";
    private static final String LOGS_DIR = "logs";

    private static volatile byte[] kek;

    private VigiloEnvironment() {
    }

    public static String getHomeDir() {
        return getSystemValue(HOME_PROPERTY);
    }

    public static Path getConfigDir() {
        String homeDir = getHomeDir();
        return homeDir == null ? null : Path.of(homeDir, CONFIG_DIR);
    }

    public static Path requireHomeDir() {
        String homeDir = getHomeDir();
        if (homeDir == null) {
            throw new IllegalStateException(HOME_PROPERTY + " is not set");
        }

        try {
            return requireDirectory(Path.of(homeDir), HOME_PROPERTY + " directory");
        } catch (InvalidPathException e) {
            throw new IllegalStateException(HOME_PROPERTY + " is not a valid path: " + homeDir, e);
        }
    }

    public static Path requireConfigDir() {
        return requireDirectory(requireHomeDir().resolve(CONFIG_DIR), "Configuration directory");
    }

    public static Path requirePropertyFilesDir() {
        return requireDirectory(requireConfigDir().resolve(PROPERTIES_DIR), "Property files directory");
    }

    public static String getLogDir() {
        String logDir = getSystemValue(LOG_HOME_PROPERTY);
        if (logDir != null) return logDir;

        String homeDir = getHomeDir();
        return homeDir == null ? null : Path.of(homeDir, LOGS_DIR).toString();
    }

    public static byte[] getKek() {
        if (kek == null) {
            String kekVar = getSystemValue(KEK_PROPERTY);
            if (kekVar != null) {
                kek = Base64.getDecoder().decode(kekVar);
            }
        }
        return kek;
    }

    static void setKek(String kekVar) {
        if (kekVar != null && !kekVar.isBlank()) {
            kek = Base64.getDecoder().decode(kekVar);
        }
    }

    private static Path requireDirectory(Path directory, String description) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException(description + " does not exist or is not a directory: " + directory);
        }
        if (!Files.isReadable(directory)) {
            throw new IllegalStateException(description + " is not readable: " + directory);
        }
        return directory;
    }

    private static String getSystemValue(String name) {
        String value = System.getenv(name);
        if (value != null && !value.isBlank()) return value;

        value = System.getProperty(name, "");
        if (value != null && !value.isBlank()) return value;

        return null;
    }
}
