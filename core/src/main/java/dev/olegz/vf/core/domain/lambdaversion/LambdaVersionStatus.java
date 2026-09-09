package dev.olegz.vf.core.domain.lambdaversion;

public enum LambdaVersionStatus {
    DRAFT,
    TESTING,
    PRODUCTION,
    DISCARDED,
    FALLBACK,
    ARCHIVED;

    public static final LambdaVersionStatus[] ALL_ACTIVE = {DRAFT, TESTING, PRODUCTION, DISCARDED};
    public static final LambdaVersionStatus[] ALL_WORKABLE = {DRAFT, TESTING, PRODUCTION, DISCARDED, FALLBACK};

    boolean isUpdatable() {
        return this == DRAFT || this == TESTING || this == DISCARDED;
    }

    public boolean isRunnable() {
        return this == TESTING || this == PRODUCTION;
    }

    public boolean isDevelopment() {
        return this == DRAFT || this == TESTING || this == DISCARDED;
    }

    public boolean canChangeTo(LambdaVersionStatus newStatus) {
        return this == TESTING && newStatus == DISCARDED;
    }

}
