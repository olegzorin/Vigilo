package dev.olegz.vf.registry.service.account;

import dev.olegz.vf.registry.domain.account.LocationUser;
import dev.olegz.vf.registry.domain.account.User;

public interface UserLocationAssignmentService {
    LocationUser assignUser(User caller, int locationId, int userId);

    void cancelAssignment(User caller, int locationId, int userId);
}
