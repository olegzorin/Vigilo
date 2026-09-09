package dev.olegz.vf.core.domain.lambdaversion;

/**
 * Semantic version increment level for a lambda's development version.
 * <p>
 * The API caller chooses only the bump level; the server derives the actual {@code major.minor.patch}
 * number from the highest version the lambda has ever used. This way developers never type a literal
 * version and can neither pick a wrong number nor collide with an existing one.
 */
public enum VersionBump {
    MAJOR,
    MINOR,
    PATCH;

    /**
     * Computes the next version string by applying this bump to {@code current}.
     * <p>
     * A {@code null}, blank or non-numeric {@code current} is treated as {@code 0.0.0}, so with no
     * prior version the first MAJOR release is {@code 1.0.0}, the first MINOR is {@code 0.1.0} and the
     * first PATCH is {@code 0.0.1}. Only the leading three numeric components of {@code current} are
     * considered; any suffix (e.g. {@code -beta}) is ignored.
     */
    public String next(String current) {
        int[] parts = parse(current); // [major, minor, patch]
        switch (this) {
            case MAJOR -> { parts[0]++; parts[1] = 0; parts[2] = 0; }
            case MINOR -> { parts[1]++; parts[2] = 0; }
            case PATCH -> parts[2]++;
        }
        return parts[0] + "." + parts[1] + "." + parts[2];
    }

    private static int[] parse(String version) {
        int[] parts = new int[3];
        if (version == null) return parts;

        int start = 0;
        for (int index = 0; (index < parts.length) && (start <= version.length()); index++) {
            int end = indexOfSeparator(version, start);
            parts[index] = leadingInt(version, start, end < 0 ? version.length() : end);
            if (end < 0) break;
            start = end + 1;
        }
        return parts;
    }

    private static int indexOfSeparator(String version, int from) {
        for (int i = from; i < version.length(); i++) {
            char c = version.charAt(i);
            if ((c == '.') || (c == '-')) return i;
        }
        return -1;
    }

    private static int leadingInt(String version, int from, int to) {
        int value = 0;
        for (int i = from; i < to; i++) {
            char c = version.charAt(i);
            if ((c < '0') || (c > '9')) break;
            value = (value * 10) + (c - '0');
        }
        return value;
    }
}
