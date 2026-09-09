package dev.olegz.vf.core.dao.impl;

import java.util.ArrayList;
import java.util.List;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.registry.cache.CacheNames;
import dev.olegz.vf.core.dao.mapper.LambdaRunMapper;
import dev.olegz.vf.core.domain.lambdarun.LambdaRuntimeAssignment;
import dev.olegz.vf.core.domain.lambdarun.LambdaRuntimeVersion;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;

/**
 * Loads immutable, location-scoped runtime configuration snapshots. Time-based assignment
 * eligibility is intentionally evaluated by the caller rather than captured in this cache.
 */
@Repository
public class LambdaRuntimeAssignmentCache {
    private final LambdaRunMapper mapper;

    public LambdaRuntimeAssignmentCache(LambdaRunMapper mapper) {
        this.mapper = mapper;
    }

    @Cacheable(cacheNames = CacheNames.LAMBDA_RUNTIME_ASSIGNMENTS)
    public List<Snapshot> getForLocation(int locationId) {
        return toSnapshots(mapper.selectRuntimeAssignmentsForLocation(locationId));
    }

    static List<Snapshot> toSnapshots(List<LambdaRuntimeAssignment> assignments) {
        if ((assignments == null) || assignments.isEmpty()) return List.of();

        List<Snapshot> snapshots = new ArrayList<>(assignments.size());
        for (LambdaRuntimeAssignment assignment : assignments) {
            if ((assignment == null) || (assignment.version == null)) continue;
            snapshots.add(Snapshot.from(assignment));
        }
        return List.copyOf(snapshots);
    }

    public record Snapshot(
        int lambdaAssignmentId,
        int lambdaId,
        int developerTeamId,
        int locationId,
        int organizationId,
        Long endTime,
        int lambdaVersionId,
        int memory,
        int timeout,
        String functionName,
        String asyncFunctionName,
        boolean published,
        int trigger)
    {
        static Snapshot from(LambdaRuntimeAssignment assignment) {
            LambdaRuntimeVersion version = assignment.version;
            return new Snapshot(
                assignment.lambdaAssignmentId,
                assignment.lambdaId,
                assignment.developerTeamId,
                assignment.locationId,
                assignment.organizationId,
                assignment.endDate != null ? assignment.endDate.getTime() : null,
                version.lambdaVersionId,
                version.memory,
                version.timeout,
                version.functionName,
                version.asyncFunctionName,
                version.published,
                version.trigger);
        }

        boolean isActiveFor(int requestedTrigger, long currentTime) {
            return ((endTime == null) || (endTime > currentTime)) && ((trigger & requestedTrigger) != 0);
        }

        LambdaRuntimeAssignment toAssignment() {
            LambdaRuntimeVersion version = new LambdaRuntimeVersion();
            version.lambdaVersionId = lambdaVersionId;
            version.memory = memory;
            version.timeout = timeout;
            version.functionName = functionName;
            version.asyncFunctionName = asyncFunctionName;
            version.published = published;
            version.trigger = trigger;

            LambdaRuntimeAssignment assignment = new LambdaRuntimeAssignment();
            assignment.lambdaAssignmentId = lambdaAssignmentId;
            assignment.lambdaId = lambdaId;
            assignment.developerTeamId = developerTeamId;
            assignment.locationId = locationId;
            assignment.organizationId = organizationId;
            assignment.endDate = endTime != null ? new Datetime(endTime) : null;
            assignment.version = version;
            return assignment;
        }
    }
}
