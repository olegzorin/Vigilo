package dev.olegz.vf.api;

import dev.olegz.vf.api.account.UserAction;
import dev.olegz.vf.api.account.UserController;
import dev.olegz.vf.api.lambda.assignment.LambdaAssignmentAction;
import dev.olegz.vf.api.lambda.assignment.LambdaAssignmentController;
import dev.olegz.vf.api.lambda.management.LambdaManagementAction;
import dev.olegz.vf.api.lambda.management.LambdaManagementController;
import dev.olegz.vf.api.location.LocationAction;
import dev.olegz.vf.api.location.LocationController;
import dev.olegz.vf.api.organization.OrganizationAction;
import dev.olegz.vf.api.organization.OrganizationController;
import dev.olegz.vf.common.VigiloEnvironment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Smoke test that the lambda developer API application context boots:
 * loads the full Spring context (core beans, datasource, sqlSessionFactory
 * with all MyBatis mappers, and the rs controllers/actions) and verifies the
 * developer API is wired. Any missing bean, broken mapper or unsatisfied
 * dependency fails context load and therefore this test.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfiguration.class)
class ApplicationContextLoadTest {

    @BeforeAll
    static void oneTimeSetUp() {
        VigiloEnvironment.getHomeDir();
    }

    private final ApplicationContext context;

    @Autowired
    ApplicationContextLoadTest(ApplicationContext context) {
        this.context = context;
    }

    @Test
    void developerApiContextLoads() {
        // /cloud/developer/ controller is wired
        assertNotNull(context.getBean(LambdaManagementController.class));
        // a lambda action singleton resolves with its full dependency graph
        assertNotNull(context.getBean(LambdaManagementAction.class));
        assertNotNull(context.getBean(LambdaAssignmentController.class));
        assertNotNull(context.getBean(LambdaAssignmentAction.class));
        // the users API is wired: controller -> action -> UsersService -> UsersDao/mapper
        assertNotNull(context.getBean(UserController.class));
        assertNotNull(context.getBean(UserAction.class));
        assertNotNull(context.getBean(OrganizationController.class));
        assertNotNull(context.getBean(OrganizationAction.class));
        assertNotNull(context.getBean(LocationController.class));
        assertNotNull(context.getBean(LocationAction.class));
    }
}
