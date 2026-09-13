package dev.olegz.vf.api.resident;

import java.util.List;
import dev.olegz.vf.api.web.support.ActionContext;
import dev.olegz.vf.api.web.support.ActionResponse;
import dev.olegz.vf.common.exception.ObjectNotFoundException;
import dev.olegz.vf.registry.dao.ResidentDao;
import dev.olegz.vf.registry.domain.account.Resident;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Component;

@Component
public class ResidentAction {
    private final ResidentDao residents;
    public ResidentAction(ResidentDao residents) { this.residents = residents; }
    public Response list(ActionContext ctx) {
        ctx.requireAdmin();
        Response response = new Response();
        response.residents = residents.getResidents(ctx.user().organizationId);
        response.collectionTotalSize = response.residents.size();
        return response;
    }
    public Response get(ActionContext ctx, int residentId) {
        ctx.requireAdmin();
        Response response = new Response();
        response.resident = requireResident(ctx, residentId);
        return response;
    }
    public Response create(ActionContext ctx, CreateRequest request) {
        ctx.requireAdmin();
        Resident resident = new Resident();
        resident.organizationId = ctx.user().organizationId;
        resident.synthetic = request.synthetic;
        apply(resident, request);
        residents.insertResident(resident);
        Response response = new Response();
        response.resident = resident;
        return response;
    }
    public Response update(ActionContext ctx, int residentId, Request request) {
        ctx.requireAdmin();
        Resident resident = requireResident(ctx, residentId);
        apply(resident, request);
        if (!residents.updateResident(resident)) throw new ObjectNotFoundException("Resident not found");
        Response response = new Response();
        response.resident = resident;
        return response;
    }
    private Resident requireResident(ActionContext ctx, int id) {
        Resident resident = residents.getResident(ctx.user().organizationId, id);
        if (resident == null) throw new ObjectNotFoundException("Resident not found");
        return resident;
    }
    private static void apply(Resident resident, Request request) {
        resident.firstName = request.firstName;
        resident.lastName = request.lastName;
        resident.email = request.email;
        resident.phone = request.phone;
    }
    public static class Request {
        public @NotBlank String firstName;
        public @NotBlank String lastName;
        public String email;
        public String phone;
    }
    public static class CreateRequest extends Request { public boolean synthetic; }
    public static class Response extends ActionResponse {
        public Resident resident;
        public List<Resident> residents;
    }
}
