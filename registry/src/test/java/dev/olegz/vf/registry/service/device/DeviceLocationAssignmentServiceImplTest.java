package dev.olegz.vf.registry.service.device;

import java.lang.reflect.Proxy;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.common.exception.DuplicateEntityException;
import dev.olegz.vf.common.exception.ObjectNotFoundException;
import dev.olegz.vf.registry.dao.DeviceDao;
import dev.olegz.vf.registry.dao.LocationDao;
import dev.olegz.vf.registry.dao.OrganizationDao;
import dev.olegz.vf.registry.domain.account.Location;
import dev.olegz.vf.registry.domain.account.Organization;
import dev.olegz.vf.registry.domain.account.User;
import dev.olegz.vf.registry.domain.device.Device;
import dev.olegz.vf.registry.domain.device.LocationDevice;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeviceLocationAssignmentServiceImplTest {

    @Test
    void assignAndCancelDeviceLocation() {
        TestFixture fixture = new TestFixture();

        LocationDevice assignment = fixture.service.assignDevice(fixture.caller, 10, "device-1");
        fixture.service.cancelAssignment(fixture.caller, 10, "device-1");

        assertEquals("device-1", assignment.deviceUuid);
        assertEquals(10, assignment.locationId);
        assertTrue(fixture.insertCalled);
        assertTrue(fixture.deleteCalled);
    }

    @Test
    void assignmentRequiresAdminAndSameOrganizationDevice() {
        TestFixture fixture = new TestFixture();
        fixture.organization.adminUserId = 9;

        assertThrows(AccessDeniedException.class,
            () -> fixture.service.assignDevice(fixture.caller, 10, "device-1"));

        fixture.organization.adminUserId = fixture.caller.userId;
        fixture.device = null;

        assertThrows(ObjectNotFoundException.class,
            () -> fixture.service.cancelAssignment(fixture.caller, 10, "device-1"));
    }

    @Test
    void duplicateAndMissingAssignmentAreReported() {
        TestFixture fixture = new TestFixture();
        fixture.insertResult = false;

        assertThrows(DuplicateEntityException.class,
            () -> fixture.service.assignDevice(fixture.caller, 10, "device-1"));

        fixture.deleteResult = false;
        assertThrows(ObjectNotFoundException.class,
            () -> fixture.service.cancelAssignment(fixture.caller, 10, "device-1"));
    }

    private static class TestFixture {
        private final User caller = user(1, 100);
        private final Location location = location(10, 100);
        private final Organization organization = organization(100, 1);
        private Device device = device("device-1", 100);
        private boolean insertResult = true;
        private boolean deleteResult = true;
        private boolean insertCalled;
        private boolean deleteCalled;
        private final DeviceLocationAssignmentService service = new DeviceLocationAssignmentServiceImpl(
            proxy(LocationDao.class, (method, args) -> method.equals("getOrganizationLocation")
                ? location : defaultValue(args.returnType())),
            proxy(OrganizationDao.class, (method, args) -> method.equals("getOrganization")
                ? organization : defaultValue(args.returnType())),
            proxy(DeviceDao.class, (method, args) -> switch (method) {
                case "getDevice" -> device;
                case "insertLocationDevice" -> {
                    insertCalled = true;
                    LocationDevice assignment = (LocationDevice) args.get(0);
                    assignment.startDate = Datetime.now();
                    yield insertResult;
                }
                case "deleteLocationDevice" -> {
                    deleteCalled = true;
                    yield deleteResult;
                }
                default -> defaultValue(args.returnType());
            }));
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

    private static Device device(String deviceUuid, int organizationId) {
        Device device = new Device();
        device.deviceUuid = deviceUuid;
        device.organizationId = organizationId;
        return device;
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
