package dev.olegz.vf.core.service.lambda;

import dev.olegz.vf.registry.service.account.LocationAssignmentChangeHandler;
import org.springframework.stereotype.Component;

/** Invalidates lambda assignments when the registry changes a location's user membership. */
@Component
public class LambdaAssignmentLocationChangeHandler implements LocationAssignmentChangeHandler {
    private final LambdaAssignmentService lambdaAssignmentService;

    public LambdaAssignmentLocationChangeHandler(LambdaAssignmentService lambdaAssignmentService) {
        this.lambdaAssignmentService = lambdaAssignmentService;
    }

    @Override
    public void locationMembershipChanged(int locationId) {
        lambdaAssignmentService.deleteAssignmentsForLocation(locationId);
    }
}
