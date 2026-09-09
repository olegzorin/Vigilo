package dev.olegz.vf.core.domain.lambdarun;

import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * Turns a raw lambda error message into a stable, invocation-independent signature so that repeated occurrences of the
 * same underlying failure can be recognized and de-duplicated in the {@code lambda_errors} table.
 * <p>
 * Lambda error messages typically carry noise that varies from one invocation to the next — timestamps, UUIDs,
 * dates, log-level markers, embedded URLs, and the lambda assignment / location / organization ids. Two messages that
 * describe the same problem therefore rarely match byte-for-byte. The methods here strip that noise so that a new
 * message can be compared against known error types rather than treated as unique every time.
 * <p>
 * There are two independent steps:
 * <ul>
 *   <li>{@link #getNormalizedError(String, int)} produces a short, human-readable summary line stored as the error's
 *       display text ({@code error_message}); and</li>
 *   <li>{@link #getErrorSignature(String, Integer...)} produces the integer key ({@code error_signature}) used to
 *       group occurrences, by removing the invocation-specific ids and hashing what remains.</li>
 * </ul>
 * All methods are stateless and side-effect free.
 */
public class LambdaErrorSignature {

    private static final String[] serviceErrors = {
        "500 Internal Server Error",
        "503 Service Unavailable",
        "Internal Server Error",
        "Service Unavailable"
    };

    private static final String[] serverErrors = {
        "Server Error",
        "Internal error"
    };

    private static final String[] errorIndicators = {
        "Error ",
        "Error: ",
        "Failure: ",
        "Unable ",
        "Failed ",
        "Unsupported ",
        "Duplicate ",
        "Cannot ",
        "Could not ",
        "Can't ",
        "Couldn't ",
        "Unknown ",
        "Wrong ",
        "Invalid ",
        "Runtime exited ",
        "Task timed out "
    };

    /**
     * Extracts a concise, normalized summary line from a raw lambda error message, suitable for display and for
     * telling apart different error types. Invocation-specific noise (dates, times, UUIDs, timestamps, URLs) is
     * stripped, and the most descriptive error sentence is selected — a known service error, a
     * {@code NamedException:} line, a recognized error indicator, and so on.
     *
     * @param message the raw lambda error message
     * @param maxSize the maximum length of the returned summary; longer results are truncated with an ellipsis
     * @return the normalized, size-bounded error summary
     */
    public static String getNormalizedError(String message, int maxSize) {
        message = cleanse(message);
        String normalizedError = extractNormalizedError(message);
        return trimToSize(normalizedError, maxSize);
    }

    private static String cleanse(String str) {
        return str.replace("\\n", "\n")
            .replaceAll("-{2,}", "--")  // shrink dash sequences
            .replaceAll("\\.{3,}", "...") // shrink dot sequences
            .replaceAll(",\\s+", ", ") // remove 'vertical' spaces within phrases, if any
            .replaceAll("(\\p{XDigit}{4,}-)+\\p{XDigit}{4,}", "") // remove UUIDs
            .replaceAll("20\\d{2}(-\\d{2}){2}([T ]\\d{1,2}(:\\d{2}){2}(\\.\\d+)?Z?)?", "") // remove dates and times
            .replaceAll("\\d{10}\\.\\d+:?\\h?", "\n") // replace timestamps with newlines
            .replaceAll("([^\\[])(WARN|INFO)", "$1\n$2") // fix typos such as missing spaces
            .replaceAll("\\S*https?://", "\n");
    }

    private static String trimToSize(String str, int maxSize) {
        str = str.replaceAll("^\\s+", "") // trim head
            .replaceAll("[:,{=\\- ]+$", "") // trim tail
            .replaceAll("\\s+", " "); // remove repetitive white spaces

        if (str.length() > maxSize) {
            int end = str.lastIndexOf(' ', maxSize - 4);
            if (end < 0) end = maxSize - 4;
            str = str.substring(0, end) + " ...";
        }
        return str;
    }

    private static int restOfLine(String str, int start) {
        int end = str.indexOf('\n', start);
        return end > start ? end : str.length();
    }

    private static String extractNormalizedError(String str) {
        for (String err : serviceErrors) {
            if (str.contains(err)) return err;
        }

        Pattern namedErrorPattern = Pattern.compile("\\p{Lu}\\p{L}+(Error|Exception):");
        MatchResult lastRes = namedErrorPattern.matcher(str).results().reduce((curr, last) -> last).orElse(null);
        if (lastRes != null) {
            int end = restOfLine(str, lastRes.end());
            return str.substring(lastRes.start(), end);
        }
        for (String p : errorIndicators) {
            int start = str.indexOf(p);
            if (start < 0) continue;
            int end = restOfLine(str, start + p.length());
            return str.substring(start, end);
        }
        for (String err : serverErrors) {
            if (str.contains(err)) return err;
        }
        String[] keywords = {"ERROR:", "[ERROR]"};
        for (String k : keywords) {
            int start = str.indexOf(k);
            if (start < 0) continue;
            start += k.length();
            int end = restOfLine(str, start);
            return str.substring(start, end);
        }

        return str;
    }

    /**
     * Computes the grouping signature for an error by removing the given invocation-specific ids (e.g. lambda
     * assignment, location, organization) from the text and hashing what remains. Occurrences of the same
     * underlying error from different assignments therefore collapse to the same signature.
     *
     * @param text        the (already normalized) error text
     * @param idsToRemove ids to strip before hashing; {@code null} ids are ignored
     * @return the hash-based signature used as the {@code error_signature} key
     */
    public static int getErrorSignature(String text, Integer... idsToRemove) {
        for (Integer id : idsToRemove) {
            if (id != null) {
                text = text.replaceAll("(?<!\\d)" + id + "(?!\\d)", "");
            }
        }
        return text.hashCode();
    }
}
