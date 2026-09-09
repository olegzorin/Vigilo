package dev.olegz.vf.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.olegz.vf.common.props.ConfigHomeProperty;
import dev.olegz.vf.common.props.PropertyStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VigiloEnvironmentTest {
    @Test
    void getKek() {
        assertNotNull(VigiloEnvironment.getKek(), "KEK is not set. Check VF_KEK system property and/or environment variable");
    }

    @Test
    void propertyConfigurationRejectsUnsetHomeDirectory() throws Exception {
        assertPropertyConfiguration(null, false, "VF_HOME is not set");
    }

    @Test
    void propertyConfigurationRejectsMissingHomeDirectory(@TempDir Path tempDir) throws Exception {
        assertPropertyConfiguration(tempDir.resolve("missing"), false, "VF_HOME directory does not exist");
    }

    @Test
    void propertyConfigurationRejectsMissingPropertyFilesDirectory(@TempDir Path tempDir) throws Exception {
        Path homeDir = tempDir.resolve("home");
        Files.createDirectories(homeDir.resolve("config"));
        assertPropertyConfiguration(homeDir, false, "Property files directory does not exist");
    }

    @Test
    void propertyConfigurationAcceptsAvailableDirectories(@TempDir Path tempDir) throws Exception {
        Path homeDir = tempDir.resolve("home");
        Files.createDirectories(homeDir.resolve("config/properties"));
        assertPropertyConfiguration(homeDir, true, "");
    }

    private static void assertPropertyConfiguration(Path homeDir, boolean expectedValid, String expectedMessage)
        throws IOException, InterruptedException
    {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        if (homeDir != null) {
            command.add("-DVF_HOME=" + homeDir);
        }
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(PropertyConfigurationProcess.class.getName());
        command.add(Boolean.toString(expectedValid));
        command.add(expectedMessage);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.environment().remove("VF_HOME");
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        assertEquals(0, exitCode, output);
    }

    public static class PropertyConfigurationProcess {
        public static void main(String[] args) {
            boolean expectedValid = Boolean.parseBoolean(args[0]);
            String expectedMessage = args[1];
            Path configDir = VigiloEnvironment.getConfigDir();
            String logbackConfigDir = new ConfigHomeProperty().getPropertyValue();

            if ((configDir == null) != (logbackConfigDir == null)) {
                throw new AssertionError("ConfigHomeProperty does not preserve the environment null contract");
            }

            try {
                PropertyStore.start();
                if (!expectedValid) {
                    throw new AssertionError("Expected home-directory validation to fail");
                }
                PropertyStore.shutdown();
            } catch (IllegalStateException e) {
                if (expectedValid) {
                    throw e;
                }
                if (!e.getMessage().contains(expectedMessage)) {
                    throw new AssertionError("Unexpected validation error: " + e.getMessage(), e);
                }
            }
        }
    }
}
