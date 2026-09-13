package dev.olegz.vf.core.service.dev;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.BiFunction;
import dev.olegz.vf.common.exception.*;
import dev.olegz.vf.core.dao.DevTeamDao;
import dev.olegz.vf.core.domain.lambdaversion.DevTeam;
import dev.olegz.vf.registry.dao.*;
import dev.olegz.vf.registry.domain.account.*;
import dev.olegz.vf.registry.service.account.AccessService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DevTeamsServiceImplTest {
    @Test
    void adminCreatesTeamAndSandboxButOwnerCannotManageMembershipOrLocationGrants() {
        Fixture f = new Fixture();
        assertThrows(AccessDeniedException.class, () -> f.service.createDevTeam(f.developer, 2, "Team", null));
        DevTeam team = f.service.createDevTeam(f.admin, 2, "Team", "Description");
        assertEquals(100, team.organizationId);
        assertEquals(LocationType.TESTING, f.locations.get(team.testingLocationId).locationType);
        assertTrue(f.members.contains(2));
        assertTrue(f.grants.contains(team.testingLocationId));
        assertThrows(AccessDeniedException.class, () -> f.service.addDevTeamMember(f.developer, 1, 3));
        assertThrows(AccessDeniedException.class, () -> f.service.grantTestingLocation(f.developer, 1, 10));
        f.service.addDevTeamMember(f.admin, 1, 3);
        assertThrows(OperationNotAllowedException.class, () -> f.service.deleteDevTeamMember(f.admin, 1, 2));
        f.service.setOwner(f.admin, 1, 3);
        f.service.deleteDevTeamMember(f.admin, 1, 2);
        assertFalse(f.members.contains(2));
        assertFalse(f.access.canAccess(f.developer, f.locations.get(team.testingLocationId)));
        assertThrows(AccessDeniedException.class, () -> f.service.getDevTeam(f.developer, 1));
    }
    @Test
    void crossOrganizationMembersAndOperationalLocationGrantsAreRejected() {
        Fixture f = new Fixture();
        f.service.createDevTeam(f.admin, 2, "Team", null);
        User foreign = user(4, 200); f.users.put(4, foreign);
        assertThrows(AccessDeniedException.class, () -> f.service.addDevTeamMember(f.admin, 1, 4));
        assertThrows(ObjectNotFoundException.class, () -> f.service.getDevTeam(foreign, 1));
        Location real = new Location(); real.locationId = 50; real.organizationId = 100;
        f.locations.put(50, real);
        assertThrows(AccessDeniedException.class, () -> f.service.grantTestingLocation(f.admin, 1, 50));
        assertThrows(AccessDeniedException.class, () -> f.service.getDevTeams(f.developer, 3));
        f.service.revokeTestingLocation(f.admin, 1, 10);
        assertFalse(f.access.canAccess(f.developer, f.locations.get(10)));
    }
    @Test
    void locationTeamsAreRestrictedToOrganizationAndActiveMembership() {
        Fixture f = new Fixture();
        DevTeam own = f.service.createDevTeam(f.admin, 2, "Own", null);
        DevTeam other = new DevTeam(3, "Other", null); other.devTeamId = 2; other.organizationId = 100;
        DevTeam foreign = new DevTeam(4, "Foreign", null); foreign.devTeamId = 3; foreign.organizationId = 200;
        f.grantedTeams.addAll(List.of(own, other, foreign));
        assertEquals(List.of(own, other), f.service.getTeamsByTestingLocation(f.admin, 10));
        assertEquals(List.of(own), f.service.getTeamsByTestingLocation(f.developer, 10));
        f.service.revokeTestingLocation(f.admin, 1, 10);
        assertThrows(AccessDeniedException.class, () -> f.service.getTeamsByTestingLocation(f.developer, 10));
        Location real = new Location(); real.locationId = 50; real.organizationId = 100;
        f.locations.put(50, real);
        assertTrue(f.service.getTeamsByTestingLocation(f.admin, 50).isEmpty());
    }
    private static class Fixture {
        final User admin = user(1, 100), developer = user(2, 100);
        final Map<Integer, User> users = new HashMap<>(Map.of(1, admin, 2, developer, 3, user(3,100)));
        final Map<Integer, Location> locations = new HashMap<>();
        final Set<Integer> members = new HashSet<>(), grants = new HashSet<>();
        final List<DevTeam> grantedTeams = new ArrayList<>();
        DevTeam team;
        final LocationDao locationDao = proxy(LocationDao.class, (name, args) -> switch(name) {
            case "insertLocation" -> { Location l = (Location)args[0]; l.locationId = 10; locations.put(10,l); yield null; }
            case "getOrganizationLocation" -> locations.get((int)args[1]);
            default -> null;
        });
        final DevTeamDao teams = proxy(DevTeamDao.class, (name,args) -> switch(name) {
            case "insertDevTeam" -> { team = (DevTeam)args[0]; team.devTeamId = 1; yield null; }
            case "getDevTeam" -> team;
            case "getDevTeams" -> List.of(team);
            case "getTeamsByTestingLocation" -> grantedTeams;
            case "insertDevTeamMember" -> members.add((int)args[1]);
            case "deleteDevTeamMember" -> members.remove((int)args[1]);
            case "checkDevTeamMember" -> (int)args[0] == 1 && members.contains((int)args[1]);
            case "grantTestingLocation" -> { grants.add((int)args[1]); yield null; }
            case "revokeTestingLocation" -> { grants.remove((int)args[1]); yield null; }
            case "updateOwner" -> { team.ownerUserId = (int)args[1]; yield null; }
            default -> null;
        });
        final AccessService access = new AccessService(proxy(OrganizationDao.class, (name,args) -> {
            Organization o = new Organization(); o.organizationId = 100; o.adminUserId = 1; return o;
        }), locationDao, List.of((user,location) -> members.contains(user) && grants.contains(location)));
        final DevTeamsServiceImpl service = new DevTeamsServiceImpl(teams,
            proxy(UserDao.class,(name,args) -> users.get((int)args[0])), locationDao, access);
    }
    private static User user(int id,int org) { User u=new User();u.userId=id;u.organizationId=org;return u; }
    private static <T> T proxy(Class<T> type, BiFunction<String,Object[],Object> handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->handler.apply(m.getName(),a)));
    }
}
