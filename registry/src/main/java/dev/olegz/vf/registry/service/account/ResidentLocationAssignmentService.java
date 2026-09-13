package dev.olegz.vf.registry.service.account;

import dev.olegz.vf.registry.domain.account.LocationResident;
import dev.olegz.vf.registry.domain.account.User;

public interface ResidentLocationAssignmentService {
    LocationResident assignResident(User caller, int locationId, int residentId);

    void cancelAssignment(User caller, int locationId, int residentId);
}
