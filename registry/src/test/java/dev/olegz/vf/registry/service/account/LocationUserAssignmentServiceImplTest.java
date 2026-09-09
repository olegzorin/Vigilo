package dev.olegz.vf.registry.service.account;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.common.exception.DuplicateEntityException;
import dev.olegz.vf.common.exception.ObjectNotFoundException;
import dev.olegz.vf.registry.dao.LocationDao;
import dev.olegz.vf.registry.dao.OrganizationDao;
import dev.olegz.vf.registry.dao.UserLocationDao;
import dev.olegz.vf.registry.domain.account.Location;
import dev.olegz.vf.registry.domain.account.LocationUser;
import dev.olegz.vf.registry.domain.account.Organization;
import dev.olegz.vf.registry.domain.account.User;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocationUserAssignmentServiceImplTest {

    @Test
    void assignAndCancelChangeUserAssignmentAndCancelLocationLambdas() {
        TestFixture fixture = new TestFixture();

        LocationUser assignment = fixture.service.assignUser(fixture.caller, 10, 2);
        fixture.service.cancelAssignment(fixture.caller, 10, 2);

        assertEquals(2, assignment.userId);
        assertEquals(10, assignment.locationId);
        assertTrue(fixture.insertCalled);
        assertTrue(fixture.deleteCalled);
        assertEquals(List.of(10, 10), fixture.cancelledLambdaLocationIds);
    }

    @Test
    void assignmentChangesRequireOrganizationAdminAndSameOrganizationUser() {
        TestFixture fixture = new TestFixture();
        fixture.organization.adminUserId = 9;

        assertThrows(AccessDeniedException.class,
            () -> fixture.service.assignUser(fixture.caller, 10, 2));

        fixture.organization.adminUserId = fixture.caller.userId;
        fixture.targetUser.organizationId = 200;

        assertThrows(AccessDeniedException.class,
            () -> fixture.service.cancelAssignment(fixture.caller, 10, 2));
        assertTrue(fixture.cancelledLambdaLocationIds.isEmpty());
    }

    @Test
    void failedUserAssignmentMutationDoesNotCancelLambdaAssignments() {
        TestFixture fixture = new TestFixture();
        fixture.insertResult = false;

        assertThrows(DuplicateEntityException.class,
            () -> fixture.service.assignUser(fixture.caller, 10, 2));

        fixture.deleteResult = false;
        assertThrows(ObjectNotFoundException.class,
            () -> fixture.service.cancelAssignment(fixture.caller, 10, 2));
        assertTrue(fixture.cancelledLambdaLocationIds.isEmpty());
    }

    private static class TestFixture {
        private final User caller = user(1, 100);
        private final User targetUser = user(2, 100);
        private final Location location = location(10, 100);
        private final Organization organization = organization(100, 1);
        private final List<Integer> cancelledLambdaLocationIds = new ArrayList<>();
        private boolean insertResult = true;
        private boolean deleteResult = true;
        private boolean insertCalled;
        private boolean deleteCalled;
        private final UserLocationAssignmentService service = new UserLocationAssignmentServiceImpl(
            proxy(LocationDao.class, (method, args) -> method.equals("getOrganizationLocation")
                ? location : defaultValue(args.returnType())),
            proxy(OrganizationDao.class, (method, args) -> method.equals("getOrganization")
                ? organization : defaultValue(args.returnType())),
            proxy(UserLocationDao.class, (method, args) -> switch (method) {
                case "insertUserLocation" -> {
                    insertCalled = true;
                    LocationUser assignment = (LocationUser) args.get(0);
                    assignment.startDate = Datetime.now();
                    yield insertResult;
                }
                case "deleteUserLocation" -> {
                    deleteCalled = true;
                    yield deleteResult;
                }
                default -> defaultValue(args.returnType());
            }),
            proxy(UserService.class, (method, args) -> method.equals("getUser")
                ? targetUser : defaultValue(args.returnType())),
            List.of(cancelledLambdaLocationIds::add));
    }

    private static User user(int userId, int organizationId) {
        User user = new User();
        user.userId = userId;
        user.organizationId = organizationId;
        return user;
    }

    private static Location location(int locationId, int organizationId) {
        Location location = new Location();
        location.locationId = locationId;
        location.organizationId = organizationId;
        return location;
    }

    private static Organization organization(int organizationId, int adminUserId) {
        Organization organization = new Organization();
        organization.organizationId = organizationId;
        organization.adminUserId = adminUserId;
        return organization;
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (proxy, method, args) -> invocation.invoke(method.getName(), new Arguments(args, method.getReturnType()))));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Arguments args);
    }

    private record Arguments(Object[] values, Class<?> returnType) {
        private Object get(int index) {
            return values[index];
        }
    }
}
