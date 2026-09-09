package dev.olegz.vf.aws.local;

import java.util.ArrayList;
import java.util.List;

import dev.olegz.vf.common.io.ProcessRunner;
import dev.olegz.vf.common.props.PropertyStore;

/**
 * Thin wrapper around the local Docker CLI, shared by the local ECR ({@link LocalEcrClient}) and
 * Lambda ({@link LocalLambdaClient}) implementations. The CLI invocation is configurable via
 * {@code vf.aws.local.docker.command} (default {@code docker}); extra tokens are supported so a
 * caller can point at a non-default context, e.g. {@code docker --context colima}.
 * <p>
 * Every call shells out through {@link ProcessRunner#run}, capturing stdout and stderr separately so
 * callers can both read command output and inspect the error log.
 */
final class LocalDocker {
    private LocalDocker() {
    }

    private static final int MAX_LOG_SIZE = 8192;

    /** The configured docker command split into its tokens (never empty). */
    static List<String> command() {
        String cmd = PropertyStore.getString("vf.aws.local.docker.command", "docker");
        String[] tokens = cmd.trim().split("\\s+");
        return tokens.length == 0 ? List.of("docker") : List.of(tokens);
    }

    /**
     * Run {@code docker <args>} with no input and the given timeout, returning the completed
     * process (stdout in {@code getOut()}, stderr in {@code getLog()}, success via {@code isOk()}).
     */
    static ProcessRunner.CompletedProcess run(List<String> args, long timeoutMs) {
        List<String> full = new ArrayList<>(command());
        full.addAll(args);
        // No redirectErrorStream() so stdout (e.g. a container id) and stderr (errors) stay separate.
        ProcessBuilder pb = new ProcessBuilder(full);
        return ProcessRunner.run(pb, null, timeoutMs, MAX_LOG_SIZE);
    }
}
