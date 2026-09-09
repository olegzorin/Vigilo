package dev.olegz.vf.core.domain.lambdaversion;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import dev.olegz.vf.common.ApplicationFailureException;

/**
 * A snapshot of all versions of a single lambda, partitioned by their lifecycle role.
 * <p>
 * At any point in time a lambda has at most one version in each of the leading roles and any number of
 * archived versions:
 * <ul>
 *   <li>{@link #devVersion} — the version under development ({@code DRAFT}, {@code UNDER_TEST} or {@code DISCARDED});</li>
 *   <li>{@link #pubVersion} — the version currently in {@code PRODUCTION};</li>
 *   <li>{@link #fallbackVersion} — the previous production version, kept as a {@code FALLBACK} to roll back to;</li>
 *   <li>{@link #archivedVersions} — all retired ({@code ARCHIVED}) versions.</li>
 * </ul>
 * Instances are created by {@link #of(List, boolean)}, which validates the version set and enforces the
 * "at most one" invariant for the dev, production and fallback roles. Callers mutate the roles (e.g. via
 * {@link LambdaVersion#updateStatus}) to describe a status transition, then persist the result via
 * {@link #toList()}.
 */
public class LambdaVersionState {
    public final LambdaVersion devVersion;
    public LambdaVersion pubVersion;
    public LambdaVersion fallbackVersion;
    public final List<LambdaVersion> archivedVersions;

    private LambdaVersionState(LambdaVersion devVersion, LambdaVersion pubVersion, LambdaVersion fallbackVersion, List<LambdaVersion> archivedVersions) {
        this.devVersion = devVersion;
        this.pubVersion = pubVersion;
        this.fallbackVersion = fallbackVersion;
        this.archivedVersions = archivedVersions;
    }

    public static LambdaVersionState of(List<LambdaVersion> lambdaVersions, boolean autoCorrect) {
        LambdaVersion devVersion = null;
        LambdaVersion pubVersion = null;
        LambdaVersion fallbackVersion = null;
        List<LambdaVersion> archivedVersions = null;

        for (LambdaVersion lambdaVersion : lambdaVersions) {
            if (lambdaVersion == null) {
                throw new ApplicationFailureException("Lambda version is null, lambdaVersions=" + printVersions(lambdaVersions));
            }

            if (lambdaVersion.status.isDevelopment()) {
                if (devVersion != null) {
                    throw new ApplicationFailureException("Duplicate dev version, lambdaVersions=" + printVersions(lambdaVersions));
                }
                devVersion = lambdaVersion;
            } else if (LambdaVersionStatus.PRODUCTION == lambdaVersion.status) {
                if (pubVersion != null) {
                    throw new ApplicationFailureException("Duplicate pub version, lambdaVersions=" + printVersions(lambdaVersions));
                }
                pubVersion = lambdaVersion;
            } else if (LambdaVersionStatus.FALLBACK == lambdaVersion.status) {
                if (fallbackVersion != null) {
                    throw new ApplicationFailureException("Duplicate fallback version, lambdaVersions=" + printVersions(lambdaVersions));
                }
                fallbackVersion = lambdaVersion;
            } else {
                if (archivedVersions == null) {
                    archivedVersions = new ArrayList<>();
                }
                archivedVersions.add(lambdaVersion);
            }
        }

        String error;
        if (devVersion != null) {
            if ((error = devVersion.checkRunnable()) != null) {
                throw new ApplicationFailureException("Dev version validation error, lambdaVersionId=" + devVersion.lambdaVersionId + " : " + error);
            }

            byte latest = devVersion.isRunnable() ? LambdaVersion.LATEST_TRUE : LambdaVersion.LATEST_NONE;
            if (autoCorrect) {
                devVersion.published = false;
                devVersion.latest = latest;
            } else if (devVersion.published || (devVersion.latest != latest)) {
                throw new ApplicationFailureException("Dev version has wrong parameters: " + printVersion(devVersion));
            }
        }

        if (pubVersion != null) {
            if ((error = pubVersion.checkRunnable()) != null) {
                throw new ApplicationFailureException("Pub version validation error, lambdaVersionId=" + pubVersion.lambdaVersionId + " : " + error);
            }

            byte latest = (devVersion == null) || !devVersion.isRunnable() ? LambdaVersion.LATEST_TRUE : LambdaVersion.LATEST_FALSE;
            if (autoCorrect) {
                pubVersion.published = true;
                pubVersion.latest = latest;
            } else if (!pubVersion.published || (pubVersion.latest != latest)) {
                throw new ApplicationFailureException("Pub version has wrong parameters: " + printVersion(pubVersion) + ", developer version=" + printVersion(devVersion));
            }
        }

        return new LambdaVersionState(devVersion, pubVersion, fallbackVersion, archivedVersions);
    }

    public List<LambdaVersion> toList() {
        ArrayList<LambdaVersion> versions = new ArrayList<>();
        if (pubVersion != null) versions.add(pubVersion);
        if (devVersion != null) versions.add(devVersion);
        if (fallbackVersion != null) versions.add(fallbackVersion);
        if (archivedVersions != null) versions.addAll(archivedVersions);
        return versions;
    }

    private static String printVersions(List<LambdaVersion> versions) {
        return versions.stream().map(LambdaVersionState::printVersion).collect(Collectors.joining(", ", "[", "]"));
    }

    private static String printVersion(LambdaVersion v) {
        return v == null ? "{null}" : "{lambdaId=" + v.lambdaId + ", lambdaVersionId=" + v.lambdaVersionId + ", status=" + v.status +
            ", published=" + v.published + ", latest=" + v.latest + "}";
    }

}
