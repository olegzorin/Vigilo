package dev.olegz.vf.api.resident;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import dev.olegz.vf.api.web.support.ActionContext;
import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.common.objectmap.StringMapper;
import dev.olegz.vf.registry.dao.OrganizationDao;
import dev.olegz.vf.registry.dao.ResidentDao;
import dev.olegz.vf.registry.domain.account.Organization;
import dev.olegz.vf.registry.domain.account.Resident;
import dev.olegz.vf.registry.domain.account.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResidentActionTest {
    @Test
    void onlyAdministratorRegistersResidentAndNoCredentialsAreExposed() {
        AtomicReference<Resident> saved = new AtomicReference<>();
        ResidentDao dao = (ResidentDao) Proxy.newProxyInstance(ResidentDao.class.getClassLoader(),
            new Class<?>[]{ResidentDao.class}, (proxy, method, args) -> {
                if (!method.getName().equals("insertResident")) throw new AssertionError(method.getName());
                Resident resident = (Resident) args[0];
                resident.residentId = 12;
                saved.set(resident);
                return null;
            });
        ResidentAction action = new ResidentAction(dao);
        ResidentAction.CreateRequest request = new ResidentAction.CreateRequest();
        request.firstName = "Test";
        request.lastName = "Resident";
        assertThrows(AccessDeniedException.class, () -> action.create(context(2), request));
        assertNull(saved.get());
        Resident resident = action.create(context(1), request).resident;
        assertEquals(100, resident.organizationId);
        assertFalse(resident.synthetic);
        var json = StringMapper.valueToTree(resident);
        assertFalse(json.has("username"));
        assertFalse(json.has("password"));
        assertFalse(json.has("apiKey"));
        assertFalse(json.has("userId"));
    }

    private static ActionContext context(int id) {
        User user = new User();
        user.userId = id;
        user.organizationId = 100;
        OrganizationDao organizations = (OrganizationDao) Proxy.newProxyInstance(
            OrganizationDao.class.getClassLoader(), new Class<?>[]{OrganizationDao.class}, (proxy, method, args) -> {
                Organization org = new Organization();
                org.adminUserId = 1;
                return org;
            });
        return new ActionContext(user, organizations);
    }
}
