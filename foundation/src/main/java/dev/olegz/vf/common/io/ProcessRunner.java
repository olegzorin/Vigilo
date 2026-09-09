package dev.olegz.vf.common.io;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import dev.olegz.vf.common.ApplicationFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProcessRunner {
    private static final Logger logger = LoggerFactory.getLogger(ProcessRunner.class);

    private static final int EXIT_CODE_OK = 0;
    private static final int EXIT_CODE_UNDEFINED = -1;
    private static final int EXIT_CODE_IO_ERROR = 128;
    private static final int DEFAULT_MAX_BUFFER_SIZE = 1024 * 1024;

    public static final class CompletedProcess {
        private final List<String> command;
        private final AtomicInteger exitCode;

        private final CircularByteBuffer logBuffer;
        private CircularByteBuffer outBuffer;

        private boolean hasErrors;

        private CompletedProcess(ProcessBuilder processBuilder, int maxLogSize) {
            this.command = processBuilder.command();
            this.exitCode = new AtomicInteger(EXIT_CODE_UNDEFINED);
            int logSize = maxLogSize > 0 ? maxLogSize : DEFAULT_MAX_BUFFER_SIZE;
            this.logBuffer = new CircularByteBuffer(logSize, StandardCharsets.UTF_8);
            if (!processBuilder.redirectErrorStream()) {
                this.outBuffer = new CircularByteBuffer(DEFAULT_MAX_BUFFER_SIZE, StandardCharsets.UTF_8);
            }
        }

        public String getLog() {
            return logBuffer.content();
        }

        public String getOut() {
            return outBuffer == null ? null : outBuffer.content();
        }

        public boolean isLogTruncated() {
            return logBuffer.isTruncated();
        }

        public boolean isOutTruncated() {
            return outBuffer != null && outBuffer.isTruncated();
        }

        public boolean isOk() {
            return (exitCode.get() == EXIT_CODE_OK) && !hasErrors;
        }
    }

    public static CompletedProcess run(ProcessBuilder processBuilder, byte[] input, long executionTimeoutMs, int maxLogSize) {
        CompletedProcess completedProcess = new CompletedProcess(processBuilder, maxLogSize);
        if (logger.isDebugEnabled()) {
            logger.debug(">run() command=" + completedProcess.command + ", input=" + (input != null) + ", executionTimeout=" + executionTimeoutMs);
        }

        Process process = null;
        try {
            process = processBuilder.start();
            if (input != null) {
                try (OutputStream outputStream = process.getOutputStream()) {
                    outputStream.write(input);
                }
            }
        } catch (Exception e) {
            if (process != null) process.destroy();
            throw new ApplicationFailureException("Failed to run command\n" + completedProcess.command, e);
        }

        AtomicBoolean outputReadFully = null;
        CompletableFuture<Void> processFuture;
        if (completedProcess.outBuffer != null) {
            outputReadFully = new AtomicBoolean(false);
            processFuture = CompletableFuture.allOf(
                makeExitCodeFuture(process, completedProcess.exitCode),
                makeOutReaderFuture(completedProcess.outBuffer, process.getInputStream(), outputReadFully),
                makeLogReaderFuture(completedProcess.logBuffer, process.getErrorStream())
            );
        } else {
            processFuture = CompletableFuture.allOf(
                makeExitCodeFuture(process, completedProcess.exitCode),
                makeLogReaderFuture(completedProcess.logBuffer, process.getInputStream())
            );
        }

        try {
            processFuture.get(executionTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            completedProcess.hasErrors = true;
            completedProcess.logBuffer.write("\n\nCompletedProcess error: " + e);
        } finally {
            process.destroy();
        }

        if (outputReadFully != null && !outputReadFully.get()) {
            completedProcess.hasErrors = true;
            completedProcess.logBuffer.write("\n\nCompletedProcess error: truncated process output stream");
        }
        return completedProcess;
    }

    private static CompletableFuture<Void> makeExitCodeFuture(Process process, AtomicInteger exitCode) {
        return CompletableFuture.runAsync(() -> {
            try {
                exitCode.set(process.waitFor());
            } catch (Exception e) {
                logger.error("Exception while waiting for process completion : " + e);
                exitCode.set(EXIT_CODE_IO_ERROR);
            }
        });
    }

    private static CompletableFuture<Void> makeOutReaderFuture(CircularByteBuffer dataBuffer, InputStream inpStream, AtomicBoolean allRead) {
        dataBuffer.reset();
        return CompletableFuture.runAsync(() -> {
            try (InputStream inputStream = inpStream) {
                dataBuffer.feedFrom(inputStream);
                allRead.set(true);
            } catch (Exception e) {
                logger.error("Exception in reading the process output stream : " + e);
            }
        });
    }

    private static CompletableFuture<Void> makeLogReaderFuture(CircularByteBuffer dataBuffer, InputStream inpStream) {
        dataBuffer.reset();
        return CompletableFuture.runAsync(() -> {
            try (InputStream inputStream = inpStream) {
                dataBuffer.feedFrom(inputStream);
            } catch (Exception e) {
                logger.error("Exception in reading the process log stream : " + e);
            }
        });
    }
}
