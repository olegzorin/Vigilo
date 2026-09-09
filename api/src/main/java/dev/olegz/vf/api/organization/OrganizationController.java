package dev.olegz.vf.api.organization;

import dev.olegz.vf.api.web.support.ActionContextFactory;
import dev.olegz.vf.api.web.support.ActionResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import static dev.olegz.vf.api.web.ApiHeaders.API_KEY;

@RestController("organizationController")
@RequestMapping(path = "/vf/organizations", produces = MediaType.APPLICATION_JSON_VALUE)
public class OrganizationController {
    private final OrganizationAction organizationAction;
    private final ActionContextFactory contextFactory;

    public OrganizationController(OrganizationAction organizationAction, ActionContextFactory contextFactory) {
        this.organizationAction = organizationAction;
        this.contextFactory = contextFactory;
    }

    @RequestMapping(method = RequestMethod.GET, path = "{organizationId}")
    public ActionResponse getOrganization(
        @RequestHeader(API_KEY) String key,
        @PathVariable int organizationId)
    {
        return organizationAction.getOrganization(contextFactory.current(), organizationId);
    }

    @RequestMapping(method = RequestMethod.PUT, path = "{organizationId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ActionResponse updateOrganization(
        @RequestHeader(API_KEY) String key,
        @PathVariable int organizationId,
        @Valid @RequestBody OrganizationAction.UpdateOrganizationRequest request)
    {
        return organizationAction.updateOrganization(contextFactory.current(), organizationId, request);
    }
}
