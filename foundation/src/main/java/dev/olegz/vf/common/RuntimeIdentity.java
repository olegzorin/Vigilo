package dev.olegz.vf.common;

import java.util.UUID;

/**
 * Identifies both the stable deployment node and the current JVM invocation.
 */
public final class RuntimeIdentity {
    /**
     * Stable identity shared by processes running on the same deployment node.
     */
    public static final String NODE_ID = "dev-local";

    /**
     * Unique identity generated once for the lifetime of this JVM.
     */
    public static final String INSTANCE_ID = UUID.randomUUID().toString();

    private RuntimeIdentity() {
    }
}
