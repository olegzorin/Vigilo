package dev.olegz.vf.core.dao;

import java.sql.Timestamp;
import java.util.UUID;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.core.TestConfiguration;
import dev.olegz.vf.core.dao.mapper.LambdaAlertMapper;
import dev.olegz.vf.core.domain.alert.LambdaAlert;
import dev.olegz.vf.core.domain.alert.LambdaAlertSeverity;
import dev.olegz.vf.core.domain.lambdaassignment.LambdaAssignment;
import dev.olegz.vf.core.domain.lambdaversion.DevTeam;
import dev.olegz.vf.core.domain.lambdaversion.Lambda;
import dev.olegz.vf.core.service.dev.DevTeamsService;
import dev.olegz.vf.registry.dao.*;
import dev.olegz.vf.registry.domain.account.*;
import dev.olegz.vf.registry.service.account.AccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfiguration.class)
@Transactional
@Rollback
class DeveloperAccessPersistenceTest {
    private final OrganizationDao organizations;
    private final UserDao users;
    private final LocationDao locations;
    private final DevTeamsService teams;
    private final DevTeamDao teamDao;
    private final AccessService access;
    private final LambdaDao lambdas;
    private final LambdaAssignmentDao assignments;
    private final LambdaAlertMapper alerts;

    @Autowired
    DeveloperAccessPersistenceTest(OrganizationDao organizations, UserDao users, LocationDao locations,
        DevTeamsService teams, DevTeamDao teamDao, AccessService access, LambdaDao lambdas,
        LambdaAssignmentDao assignments, LambdaAlertMapper alerts) {
        this.organizations = organizations;
        this.users = users;
        this.locations = locations;
        this.teams = teams;
        this.teamDao = teamDao;
        this.access = access;
        this.lambdas = lambdas;
        this.assignments = assignments;
        this.alerts = alerts;
    }

    @Test
    void provisioningGrantsRevocationAndTestingPersistenceRoundTrip() {
        Organization org = new Organization();
        org.organizationName = "Access test";
        organizations.insertOrganization(org);
        User admin = user(org.organizationId, AccountType.ADMINISTRATOR);
        User developer = user(org.organizationId, AccountType.DEVELOPER);
        org.adminUserId = admin.userId;
        organizations.updateOrganization(org);
        DevTeam team = teams.createDevTeam(admin, developer.userId, "Team " + UUID.randomUUID(), null);
        Location location = locations.getOrganizationLocation(org.organizationId, team.testingLocationId);
        assertEquals(LocationType.TESTING, location.locationType);
        assertTrue(teamDao.hasTestingLocationAccess(developer.userId, location.locationId));
        assertTrue(access.canAccess(developer, location));
        assertEquals(location.locationId, team.testingLocations.getFirst().locationId);
        assertEquals(team.devTeamId, teams.getTeamsByTestingLocation(admin, location.locationId).getFirst().devTeamId);
        teams.revokeTestingLocation(admin, team.devTeamId, location.locationId);
        assertFalse(access.canAccess(developer, location));
        assertTrue(teams.getDevTeam(admin, team.devTeamId).testingLocations.isEmpty());
        assertTrue(teams.getTeamsByTestingLocation(admin, location.locationId).isEmpty());
        teams.grantTestingLocation(admin, team.devTeamId, location.locationId);
        assertTrue(access.canAccess(developer, location));

        Location extra = new Location();
        extra.organizationId = org.organizationId;
        extra.locationName = "Additional testing";
        extra.locationType = LocationType.TESTING;
        extra.address = new Address();
        locations.insertLocation(extra);
        teams.grantTestingLocation(admin, team.devTeamId, extra.locationId);
        assertEquals(java.util.List.of(location.locationId, extra.locationId),
            teams.getDevTeam(developer, team.devTeamId).testingLocations.stream().map(l -> l.locationId).toList());
        assertEquals(team.devTeamId, teams.getTeamsByTestingLocation(developer, extra.locationId).getFirst().devTeamId);
        locations.deleteLocation(extra.locationId);
        assertEquals(1, teams.getDevTeam(admin, team.devTeamId).testingLocations.size());

        Lambda lambda = new Lambda();
        lambda.lambdaName = "Test " + UUID.randomUUID();
        lambda.devTeamId = team.devTeamId;
        lambda.createdAt = Datetime.now();
        lambdas.insertLambda(lambda);
        LambdaAssignment assignment = new LambdaAssignment(lambda.lambdaId, location.locationId, true);
        assignments.insertLambdaAssignment(assignment);
        assertEquals(assignment.lambdaAssignmentId,
            assignments.getLambdaAssignments(lambda.lambdaId, null, false).getFirst().lambdaAssignmentId,
            "Assignments without a runnable version must remain manageable");

        assertNull(assignments.getLambdaAssignments(lambda.lambdaId, null, false).getFirst().lambdaVersion);

        LambdaAlert alert = new LambdaAlert();
        alert.alertId = UUID.randomUUID().toString();
        alert.idempotencyKey = "test-alert";
        alert.payloadHash = "hash";
        alert.locationId = location.locationId;
        alert.alertType = "DANGEROUS_STATE_CHANGE";
        alert.severity = LambdaAlertSeverity.values()[0];
        alert.status = "OPEN";
        alert.ruleId = "test-rule";
        alert.occurredAt = new Timestamp(System.currentTimeMillis());
        alert.lambdaAssignmentId = assignment.lambdaAssignmentId;
        alert.lambdaId = lambda.lambdaId;
        alert.lambdaVersionId = 1;
        alert.runId = 1;
        alert.eventKey = "test-event";
        alert.changesJson = "[]";
        assertEquals(1, alerts.insertAlert(alert));
        assertTrue(alert.testing);
        assertTrue(alerts.selectAlert(alert.idempotencyKey, assignment.lambdaAssignmentId).testing);
    }

    private User user(int organizationId, AccountType type) {
        User user = new User();
        user.organizationId = organizationId;
        user.accountType = type;
        user.username = UUID.randomUUID().toString();
        users.insertUser(user);
        return user;
    }
}
