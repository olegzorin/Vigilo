package dev.olegz.vf.core.service.dev;

import java.util.*;

import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.common.exception.OperationNotAllowedException;
import dev.olegz.vf.core.dao.DevTeamDao;
import dev.olegz.vf.registry.dao.UserDao;
import dev.olegz.vf.registry.domain.account.User;
import dev.olegz.vf.core.domain.lambdaversion.DevTeam;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DevTeamsServiceImplTest {

    @Test
    void createDevTeam_setsCallerAsOwnerAndMember() {
        TestFixture fixture = new TestFixture();

        DevTeam team = fixture.service.createDevTeam(1, "Owners", "Description");

        assertEquals(1, team.ownerUserId);
        assertTrue(fixture.devTeamDao.checkDevTeamMember(team.devTeamId, 1));
    }

    @Test
    void onlyOwnerCanAddAndDeleteMembers() {
        TestFixture fixture = new TestFixture();
        DevTeam team = fixture.service.createDevTeam(1, "Owners", null);

        assertThrows(AccessDeniedException.class,
            () -> fixture.service.addDevTeamMember(2, team.devTeamId, 2));

        fixture.service.addDevTeamMember(1, team.devTeamId, 2);
        assertTrue(fixture.devTeamDao.checkDevTeamMember(team.devTeamId, 2));

        assertThrows(AccessDeniedException.class,
            () -> fixture.service.deleteDevTeamMember(2, team.devTeamId, 2));

        fixture.service.deleteDevTeamMember(1, team.devTeamId, 2);
        assertTrue(!fixture.devTeamDao.checkDevTeamMember(team.devTeamId, 2));
    }

    @Test
    void ownerCannotRemoveThemselves() {
        TestFixture fixture = new TestFixture();
        DevTeam team = fixture.service.createDevTeam(1, "Owners", null);

        assertThrows(OperationNotAllowedException.class,
            () -> fixture.service.deleteDevTeamMember(1, team.devTeamId, 1));
        assertTrue(fixture.devTeamDao.checkDevTeamMember(team.devTeamId, 1));
    }

    private static class TestFixture {
        private final FakeDevTeamDao devTeamDao = new FakeDevTeamDao();
        private final FakeUserDao userDao = new FakeUserDao();
        private final DevTeamsService service = new DevTeamsServiceImpl(devTeamDao, userDao);

        TestFixture() {
            userDao.add(1);
            userDao.add(2);
        }
    }

    private static class FakeDevTeamDao implements DevTeamDao {
        private final Map<Integer, DevTeam> teams = new HashMap<>();
        private final Map<Integer, Set<Integer>> members = new HashMap<>();
        private int nextId = 1;

        @Override
        public List<DevTeam> getDevTeams(Integer userId) {
            return new ArrayList<>(teams.values());
        }

        @Override
        public DevTeam getDevTeam(int devTeamId) {
            return teams.get(devTeamId);
        }

        @Override
        public boolean checkDevTeamMember(int devTeamId, int userId) {
            return members.getOrDefault(devTeamId, Set.of()).contains(userId);
        }

        @Override
        public void insertDevTeam(DevTeam devTeam) {
            devTeam.devTeamId = nextId++;
            teams.put(devTeam.devTeamId, devTeam);
            members.put(devTeam.devTeamId, new HashSet<>());
        }

        @Override
        public boolean insertDevTeamMember(int devTeamId, int userId) {
            return members.get(devTeamId).add(userId);
        }

        @Override
        public boolean deleteDevTeamMember(int devTeamId, int userId) {
            return members.get(devTeamId).remove(userId);
        }
    }

    private static class FakeUserDao implements UserDao {
        private final Map<Integer, User> users = new HashMap<>();

        void add(int userId) {
            User user = new User();
            user.userId = userId;
            users.put(userId, user);
        }

        @Override public void insertUser(User user) { users.put(user.userId, user); }
        @Override public User getUser(int userId) { return users.get(userId); }
        @Override public List<User> getUsers(Integer organizationId, Integer userId, String firstName,
                                             String lastName, String email) { return List.of(); }
        @Override public List<User> getUsersByLocation(int locationId) { return List.of(); }
        @Override public User getUserByUsername(String username) { return null; }
        @Override public boolean updateUser(User user) { return false; }
        @Override public boolean updatePassword(int userId, String password) { return false; }
        @Override public boolean deleteUser(int userId) { return false; }
    }
}
