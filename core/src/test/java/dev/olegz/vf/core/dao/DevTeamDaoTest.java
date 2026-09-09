package dev.olegz.vf.core.dao;

import java.util.List;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.VigiloEnvironment;
import dev.olegz.vf.common.util.CollectionOps;
import dev.olegz.vf.core.TestConfiguration;
import dev.olegz.vf.core.dao.mapper.DevTeamMapper;
import dev.olegz.vf.registry.dao.UserDao;
import dev.olegz.vf.registry.domain.account.User;
import dev.olegz.vf.core.domain.lambdaversion.Lambda;
import dev.olegz.vf.core.domain.lambdaversion.DevTeam;
import dev.olegz.vf.core.domain.lambdaversion.DevTeamMember;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

/**
 * DAO tests for {@link DevTeamDao}. Each test seeds its own users, dev teams, members and lambdas
 * via the mapper and runs inside a transaction that is rolled back afterwards, so no data leaks
 * between tests or into the database.
 * <p>
 * {@code getDevTeams} (list view) exposes only {@code membersCount} (active members) and
 * {@code lambdasCount}; {@code getDevTeam} (detail view) exposes the {@code members} and {@code lambdas}
 * collections instead.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfiguration.class)
@Transactional
@Rollback
class DevTeamDaoTest {

    @BeforeAll
    static void oneTimeSetUp() {
        VigiloEnvironment.getHomeDir();
    }

    private final DevTeamDao devTeamDao;
    private final DevTeamMapper mapper;
    private final LambdaDao lambdaDao;
    private final UserDao userDao;

    @Autowired
    DevTeamDaoTest(DevTeamDao devTeamDao, DevTeamMapper mapper, LambdaDao lambdaDao, UserDao userDao) {
        this.devTeamDao = devTeamDao;
        this.mapper = mapper;
        this.lambdaDao = lambdaDao;
        this.userDao = userDao;
    }

    private int insertUser() {
        User user = new User();
        userDao.insertUser(user);
        return user.userId;
    }

    private int insertTeam(String name, String description) {
        DevTeam team = new DevTeam(insertUser(), name, description);
        mapper.insertDevTeam(team);
        return team.devTeamId;
    }

    private int insertLambda(int teamId, String name) {
        Lambda lambda = new Lambda();
        lambda.lambdaName = name;
        lambda.devTeamId = teamId;
        lambda.createdAt = Datetime.now();
        lambdaDao.insertLambda(lambda);
        return lambda.lambdaId;
    }

    // --- getDevTeam (detail view) ------------------------------------------------------

    @Test
    void getDevTeam_returnsMembersAndLambdas_withoutCounts() {
        int teamId = insertTeam("QA Team", "Quality assurance");
        int activeUserId = insertUser();
        int endingUserId = insertUser();
        mapper.insertDevTeamMember(teamId, activeUserId, Datetime.nowMinusDays(10), null);
        mapper.insertDevTeamMember(teamId, endingUserId, Datetime.nowMinusDays(5), Datetime.nowPlusDays(5));
        int lambdaId = insertLambda(teamId, "lambda_" + teamId + "_a");
        insertLambda(teamId, "lambda_" + teamId + "_b");

        DevTeam team = devTeamDao.getDevTeam(teamId);

        assertNotNull(team);
        assertEquals(teamId, team.devTeamId);
        assertTrue(team.ownerUserId > 0);
        assertEquals("QA Team", team.name);
        assertEquals("Quality assurance", team.description);

        assertNotNull(team.members);
        assertEquals(2, team.members.size());
        DevTeamMember member = CollectionOps.findAny(team.members, m -> m.userId == activeUserId);
        assertNotNull(member);
        assertNotNull(member.startDate);
        assertNull(member.endDate);

        assertNotNull(team.lambdas);
        assertEquals(2, team.lambdas.size());
        Lambda lambda = CollectionOps.findAny(team.lambdas, b -> b.lambdaId == lambdaId);
        assertNotNull(lambda);
        assertEquals("lambda_" + teamId + "_a", lambda.lambdaName);

        // Counts are reserved for the list view.
        assertEquals(0, team.membersCount);
        assertEquals(0, team.lambdasCount);
    }

    @Test
    void getDevTeam_teamWithoutMembersOrLambdas_returnsEmptyCollections() {
        int teamId = insertTeam("Empty Team", null);

        DevTeam team = devTeamDao.getDevTeam(teamId);

        assertNotNull(team);
        assertEquals("Empty Team", team.name);
        assertNull(team.description);
        assertNotNull(team.members);
        assertTrue(team.members.isEmpty());
        assertNotNull(team.lambdas);
        assertTrue(team.lambdas.isEmpty());
    }

    @Test
    void getDevTeam_unknownId_returnsNull() {
        assertNull(devTeamDao.getDevTeam(-1));
    }

    // --- getDevTeams (list view) -------------------------------------------------------

    @Test
    void getDevTeams_returnsCounts_withoutCollections() {
        int teamId = insertTeam("Counted Team", null);
        int activeUser1 = insertUser();
        int activeUser2 = insertUser();
        int expiredUser = insertUser();
        mapper.insertDevTeamMember(teamId, activeUser1, Datetime.nowMinusDays(2), null);
        mapper.insertDevTeamMember(teamId, activeUser2, Datetime.nowMinusDays(2), Datetime.nowPlusDays(2));
        mapper.insertDevTeamMember(teamId, expiredUser, Datetime.nowMinusDays(10), Datetime.nowMinusDays(1));
        insertLambda(teamId, "lambda_" + teamId + "_a");
        insertLambda(teamId, "lambda_" + teamId + "_b");

        List<DevTeam> teams = devTeamDao.getDevTeams(null);

        DevTeam team = CollectionOps.findAny(teams, t -> t.devTeamId == teamId);
        assertNotNull(team);
        // Only active members are counted (the expired membership is excluded).
        assertEquals(2, team.membersCount);
        assertEquals(2, team.lambdasCount);
        // Collections are reserved for the detail view.
        assertNull(team.members);
        assertNull(team.lambdas);
    }

    @Test
    void getDevTeams_byUser_returnsOnlyTeamsUserBelongsTo() {
        int teamA = insertTeam("Team A", null);
        int teamB = insertTeam("Team B", null);
        int userA = insertUser();
        int userB = insertUser();
        mapper.insertDevTeamMember(teamA, userA, Datetime.nowMinusDays(1), null);
        mapper.insertDevTeamMember(teamB, userB, Datetime.nowMinusDays(1), null);

        List<DevTeam> teams = devTeamDao.getDevTeams(userA);

        assertTrue(CollectionOps.anyMatch(teams, t -> t.devTeamId == teamA));
        assertFalse(CollectionOps.anyMatch(teams, t -> t.devTeamId == teamB));
    }

    @Test
    void getDevTeams_nullUser_returnsAllTeams() {
        int teamA = insertTeam("Team A", null);
        int teamB = insertTeam("Team B", null);

        List<DevTeam> teams = devTeamDao.getDevTeams(null);

        assertTrue(CollectionOps.anyMatch(teams, t -> t.devTeamId == teamA));
        assertTrue(CollectionOps.anyMatch(teams, t -> t.devTeamId == teamB));
    }

    // --- checkDevTeamMember ------------------------------------------------------------

    @Test
    void checkDevTeamMember_activeMember_returnsTrue() {
        int teamId = insertTeam("Active Team", null);
        int userId = insertUser();
        mapper.insertDevTeamMember(teamId, userId, Datetime.nowMinusDays(1), null);

        assertTrue(devTeamDao.checkDevTeamMember(teamId, userId));
    }

    @Test
    void checkDevTeamMember_notAMember_returnsFalse() {
        int teamId = insertTeam("Some Team", null);
        int memberId = insertUser();
        int strangerId = insertUser();
        mapper.insertDevTeamMember(teamId, memberId, Datetime.nowMinusDays(1), null);

        assertFalse(devTeamDao.checkDevTeamMember(teamId, strangerId));
    }

    @Test
    void checkDevTeamMember_expiredMembership_returnsFalse() {
        int teamId = insertTeam("Expired Team", null);
        int userId = insertUser();
        mapper.insertDevTeamMember(teamId, userId, Datetime.nowMinusDays(10), Datetime.nowMinusDays(1));

        assertFalse(devTeamDao.checkDevTeamMember(teamId, userId));
    }

    @Test
    void checkDevTeamMember_futureMembership_returnsFalse() {
        int teamId = insertTeam("Future Team", null);
        int userId = insertUser();
        mapper.insertDevTeamMember(teamId, userId, Datetime.nowPlusDays(1), null);

        assertFalse(devTeamDao.checkDevTeamMember(teamId, userId));
    }
}
