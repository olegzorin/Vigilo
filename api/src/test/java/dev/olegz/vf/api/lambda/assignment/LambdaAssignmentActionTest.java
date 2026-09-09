package dev.olegz.vf.api.lambda.assignment;

import java.util.List;

import dev.olegz.vf.api.web.support.ActionContext;
import dev.olegz.vf.common.exception.MissingParameterException;
import dev.olegz.vf.common.exception.WrongParameterValueException;
import dev.olegz.vf.registry.dao.OrganizationDao;
import dev.olegz.vf.registry.domain.account.Organization;
import dev.olegz.vf.registry.domain.account.User;
import dev.olegz.vf.core.domain.lambdaassignment.LambdaAssignment;
import dev.olegz.vf.core.domain.lambdarun.LambdaKeyInput;
import dev.olegz.vf.core.service.lambda.LambdaAssignmentService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LambdaAssignmentActionTest {

    @Test
    void createGetAndUpdateDelegateWithCurrentUser() {
        FakeLambdaAssignmentService service = new FakeLambdaAssignmentService();
        service.assignment = assignment(101, 11, 21);
        LambdaAssignmentAction action = new LambdaAssignmentAction(service);
        ActionContext context = context(7);

        LambdaAssignmentAction.CreateRequest create = new LambdaAssignmentAction.CreateRequest();
        create.lambdaId = 11;
        create.locationId = 21;
        create.testing = false;
        LambdaAssignmentAction.Response created = action.createLambdaAssignment(context, create);
        LambdaAssignmentAction.Response found = action.getLambdaAssignment(context, 101);
        LambdaAssignmentAction.LambdaApiKeyResponse key =
            action.generateLambdaApiKey(context, 101);

        LambdaAssignmentAction.UpdateRequest update = new LambdaAssignmentAction.UpdateRequest();
        update.enabled = false;
        action.updateLambdaAssignment(context, 101, update);

        assertEquals(101, created.lambdaAssignmentId);
        assertEquals(101, found.assignment.lambdaAssignmentId);
        assertEquals(11, found.assignment.lambdaId);
        assertEquals("lambda-key", key.lambdaApiKey);
        assertEquals(1234567890L, key.expiry);
        assertEquals(7, service.user.userId);
        assertEquals(11, service.lambdaId);
        assertEquals(21, service.locationId);
        assertFalse(service.enabled);
    }

    @Test
    void listRequiresExactlyOneFilterAndReturnsCollectionMetadata() {
        FakeLambdaAssignmentService service = new FakeLambdaAssignmentService();
        service.assignments = List.of(assignment(101, 11, 21), assignment(102, 11, 22));
        LambdaAssignmentAction action = new LambdaAssignmentAction(service);
        ActionContext context = context(7);

        LambdaAssignmentAction.Response byLambda = action.getLambdaAssignments(context, 11, null);
        LambdaAssignmentAction.Response byLocation = action.getLambdaAssignments(context, null, 21);

        assertEquals(2, byLambda.assignments.size());
        assertEquals(2, byLambda.collectionTotalSize);
        assertEquals(21, byLocation.assignments.getFirst().locationId);
        assertThrows(MissingParameterException.class,
            () -> action.getLambdaAssignments(context, null, null));
        assertThrows(WrongParameterValueException.class,
            () -> action.getLambdaAssignments(context, 11, 21));
    }

    @Test
    void cancelDelegatesSingleAndBulkOperations() {
        FakeLambdaAssignmentService service = new FakeLambdaAssignmentService();
        LambdaAssignmentAction action = new LambdaAssignmentAction(service);
        ActionContext context = context(7);

        action.cancelLambdaAssignment(context, 101);
        assertEquals(101, service.lambdaAssignmentId);

        action.cancelLambdaAssignments(context, 11, null);
        assertEquals("lambda", service.bulkCancelType);
        assertEquals(11, service.lambdaId);

        action.cancelLambdaAssignments(context, null, 21);
        assertEquals("location", service.bulkCancelType);
        assertEquals(21, service.locationId);
        assertThrows(WrongParameterValueException.class,
            () -> action.cancelLambdaAssignments(context, 11, 21));
    }

    @Test
    void listConvertsNullServiceResultToEmptyCollection() {
        LambdaAssignmentAction action = new LambdaAssignmentAction(new FakeLambdaAssignmentService());

        LambdaAssignmentAction.Response response = action.getLambdaAssignments(context(7), 11, null);

        assertTrue(response.assignments.isEmpty());
        assertEquals(0, response.collectionTotalSize);
    }

    private static LambdaAssignment assignment(int lambdaAssignmentId, int lambdaId, int locationId) {
        LambdaAssignment assignment = new LambdaAssignment(lambdaId, locationId, false);
        assignment.lambdaAssignmentId = lambdaAssignmentId;
        return assignment;
    }

    private static ActionContext context(int userId) {
        User user = new User();
        user.userId = userId;
        return new ActionContext(user, new OrganizationDao() {
            @Override public Organization getOrganization(int organizationId) { return null; }
            @Override public void insertOrganization(Organization organization) { throw new UnsupportedOperationException(); }
            @Override public boolean updateOrganization(Organization organization) { throw new UnsupportedOperationException(); }
            @Override public boolean deleteOrganization(int organizationId) { throw new UnsupportedOperationException(); }
        });
    }

    private static class FakeLambdaAssignmentService implements LambdaAssignmentService {
        private User user;
        private LambdaAssignment assignment;
        private List<LambdaAssignment> assignments;
        private int lambdaAssignmentId;
        private int lambdaId;
        private int locationId;
        private boolean enabled;
        private String bulkCancelType;

        @Override
        public int createLambdaAssignment(User user, int lambdaId, int locationId, boolean testing) {
            this.user = user;
            this.lambdaId = lambdaId;
            this.locationId = locationId;
            return assignment.lambdaAssignmentId;
        }

        @Override
        public LambdaAssignment getLambdaAssignment(User user, int lambdaAssignmentId) {
            this.user = user;
            this.lambdaAssignmentId = lambdaAssignmentId;
            return assignment;
        }

        @Override
        public LambdaKeyInput generateLambdaApiKey(User user, int lambdaAssignmentId) {
            this.user = user;
            this.lambdaAssignmentId = lambdaAssignmentId;
            return new LambdaKeyInput("lambda-key", 1234567890L);
        }

        @Override
        public List<LambdaAssignment> getLambdaAssignmentsForLambda(User user, int lambdaId) {
            this.user = user;
            this.lambdaId = lambdaId;
            return assignments;
        }

        @Override
        public List<LambdaAssignment> getLambdaAssignmentsForLocation(User user, int locationId) {
            this.user = user;
            this.locationId = locationId;
            return assignments;
        }

        @Override
        public void setLambdaAssignmentEnabled(User user, int lambdaAssignmentId, boolean enabled) {
            this.user = user;
            this.lambdaAssignmentId = lambdaAssignmentId;
            this.enabled = enabled;
        }

        @Override
        public void cancelLambdaAssignment(User user, int lambdaAssignmentId) {
            this.user = user;
            this.lambdaAssignmentId = lambdaAssignmentId;
        }

        @Override
        public void cancelAssignmentsForLocation(User user, int locationId) {
            this.user = user;
            this.locationId = locationId;
            bulkCancelType = "location";
        }

        @Override
        public void deleteAssignmentsForLocation(int locationId) {
            this.locationId = locationId;
            bulkCancelType = "location";
        }

        @Override
        public void cancelAssignmentsForLambda(User user, int lambdaId) {
            this.user = user;
            this.lambdaId = lambdaId;
            bulkCancelType = "lambda";
        }
    }
}
