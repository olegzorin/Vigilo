package dev.olegz.vf.aws;

/** Naming policy for resources provisioned by this application. */
public final class AwsResourceNames {
    public static final String PREFIX = "dev-botlab-";

    private AwsResourceNames() {
    }

    public static String prefixed(String name) {
        return name.startsWith(PREFIX) ? name : PREFIX + name;
    }
}
