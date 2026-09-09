package dev.olegz.vf.core.dao.impl;

import java.util.List;
import java.util.Map;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.core.domain.lambdarun.LambdaRuntimeAssignment;
import dev.olegz.vf.core.domain.lambdarun.LambdaRuntimeVersion;
import dev.olegz.vf.core.domain.lambdarun.input.LocationHydrationRow;
import dev.olegz.vf.core.domain.lambdarun.input.LocationHydrationSnapshot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LambdaRunDaoImplTest {

    @Test
    void runtimeAssignmentSnapshotsFilterTriggerAndExpiryWithoutExposingCachedObjects() {
        long now = 1_000_000L;
        LambdaRuntimeAssignment active = runtimeAssignment(1, 1, null);
        LambdaRuntimeAssignment future = runtimeAssignment(2, 2, new Datetime(now + 10_000L));
        LambdaRuntimeAssignment expired = runtimeAssignment(3, 1, new Datetime(now));
        List<LambdaRuntimeAssignmentCache.Snapshot> snapshots =
            LambdaRuntimeAssignmentCache.toSnapshots(List.of(active, future, expired));

        assertThrows(UnsupportedOperationException.class, () -> snapshots.clear());

        List<LambdaRuntimeAssignment> first = LambdaRunDaoImpl.filterRuntimeAssignments(snapshots, 1, now);
        assertEquals(List.of(1), assignmentIds(first));

        first.getFirst().version.functionName = "mutated";
        first.clear();

        List<LambdaRuntimeAssignment> second = LambdaRunDaoImpl.filterRuntimeAssignments(snapshots, 1, now);
        assertEquals(List.of(1), assignmentIds(second));
        assertEquals("function-1", second.getFirst().version.functionName);
        assertEquals(List.of(2), assignmentIds(
            LambdaRunDaoImpl.filterRuntimeAssignments(snapshots, 2, now)));
    }

    @Test
    void toLocationHydrationSnapshotBuildsImmutableTypedSnapshot() {
        LocationHydrationRow location = new LocationHydrationRow();
        location.rowType = 0;
        location.locationCurrentState = "home";

        LocationHydrationRow device = new LocationHydrationRow();
        device.rowType = 1;
        device.deviceUuid = "device-1";
        device.deviceCurrentState = Map.of("online", true);

        LocationHydrationRow user = new LocationHydrationRow();
        user.rowType = 2;
        user.userId = 101;
        user.locationAccess = 3;

        LocationHydrationSnapshot snapshot = LambdaRunDaoImpl.toLocationHydrationSnapshot(
            List.of(location, device, user));

        assertEquals("home", snapshot.currentState());
        assertEquals(1, snapshot.deviceStates().size());
        assertEquals("device-1", snapshot.deviceStates().getFirst().deviceUuid());
        assertEquals(Map.of("online", true), snapshot.deviceStates().getFirst().currentState());
        assertEquals(1, snapshot.users().size());
        assertEquals(101, snapshot.users().getFirst().userId);
        assertEquals(3, snapshot.users().getFirst().locationAccess);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.deviceStates().clear());
        assertThrows(
            UnsupportedOperationException.class,
            () -> snapshot.deviceStates().getFirst().currentState().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.users().clear());
    }

    @Test
    void toLocationHydrationSnapshotHandlesNoRows() {
        LocationHydrationSnapshot snapshot = LambdaRunDaoImpl.toLocationHydrationSnapshot(null);

        assertEquals(null, snapshot.currentState());
        assertEquals(List.of(), snapshot.deviceStates());
        assertEquals(List.of(), snapshot.users());
    }

    private static LambdaRuntimeAssignment runtimeAssignment(int assignmentId, int trigger, Datetime endDate) {
        LambdaRuntimeVersion version = new LambdaRuntimeVersion();
        version.lambdaVersionId = 100 + assignmentId;
        version.functionName = "function-" + assignmentId;
        version.trigger = trigger;

        LambdaRuntimeAssignment assignment = new LambdaRuntimeAssignment();
        assignment.lambdaAssignmentId = assignmentId;
        assignment.lambdaId = 10 + assignmentId;
        assignment.locationId = 20;
        assignment.endDate = endDate;
        assignment.version = version;
        return assignment;
    }

    private static List<Integer> assignmentIds(List<LambdaRuntimeAssignment> assignments) {
        java.util.ArrayList<Integer> ids = new java.util.ArrayList<>(assignments.size());
        for (LambdaRuntimeAssignment assignment : assignments) {
            ids.add(assignment.lambdaAssignmentId);
        }
        return ids;
    }
}
