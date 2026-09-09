package dev.olegz.vf.registry.tool;

import java.io.Console;
import java.util.HashMap;
import java.util.Map;


/**
 * Minimal {@code --key value} / {@code --key=value} command-line
 * parser shared by the account bootstrap tools.
 * A bare {@code --flag} maps to an empty string (test with {@link #has}).
 */
final class CliArgs {

    private final Map<String, String> opts;

    private CliArgs(Map<String, String> opts) {
        this.opts = opts;
    }

    static CliArgs parse(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + arg);
            }
            String key = arg.substring(2);
            int eq = key.indexOf('=');
            if (eq >= 0) {
                opts.put(key.substring(0, eq), key.substring(eq + 1));
            } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                opts.put(key, args[++i]);
            } else {
                opts.put(key, "");
            }
        }
        return new CliArgs(opts);
    }

    String get(String key) {
        return opts.get(key);
    }

    boolean has(String key) {
        return opts.containsKey(key);
    }

    String require(String key) {
        String value = opts.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required argument --" + key);
        }
        return value;
    }

    int requireInt(String key) {
        return toInt(key, require(key));
    }

    /** @return the parsed value of {@code --key}, or {@code null}
     * if it was not supplied. */
    Integer optInt(String key) {
        String value = opts.get(key);
        return value == null || value.isBlank() ? null : toInt(key, value);
    }

    private static int toInt(String key, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--" + key + " must be an integer, got '" + value + "'");
        }
    }

    /**
     * Resolve a password without echoing it into shell history:
     * {@code --password} first, then the {@code VF_ADMIN_PASSWORD}
     * environment variable, then an interactive console prompt.
     *
     * @param prompt the label shown at the console prompt (e.g.
     * {@code "New password: "})
     */
    String resolvePassword(String prompt) {
        String password = opts.get("password");
        if (password == null || password.isBlank()) {
            password = System.getenv("VF_ADMIN_PASSWORD");
        }
        if (password == null || password.isBlank()) {
            Console console = System.console();
            if (console != null) {
                char[] entered = console.readPassword(prompt);
                if (entered != null) {
                    password = new String(entered);
                }
            }
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException(
                    "Password required: pass --password, set VF_ADMIN_PASSWORD, or run on an interactive console");
        }
        return password;
    }
}
