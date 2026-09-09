package dev.olegz.vf.api.organization;

import dev.olegz.vf.api.account.ApiAddress;
import dev.olegz.vf.api.web.support.ActionContext;
import dev.olegz.vf.api.web.support.ActionResponse;
import dev.olegz.vf.common.exception.ObjectNotFoundException;
import dev.olegz.vf.registry.dao.OrganizationDao;
import dev.olegz.vf.registry.domain.account.Organization;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OrganizationAction {
    private static final Logger logger = LoggerFactory.getLogger(OrganizationAction.class);

    private final OrganizationDao organizationDao;

    public OrganizationAction(OrganizationDao organizationDao) {
        this.organizationDao = organizationDao;
    }

    public Response getOrganization(ActionContext ctx, int organizationId) {
        logger.debug(">getOrganization() organizationId={}", organizationId);
        ctx.requireSameOrganization(organizationId);

        Organization organization = getExistingOrganization(organizationId);

        Response response = new Response();
        response.organization = new ApiOrganization(organization);

        logger.debug("<getOrganization() organizationId={}", organizationId);
        return response;
    }

    public Response updateOrganization(ActionContext ctx, int organizationId, UpdateOrganizationRequest request) {
        logger.debug(">updateOrganization() organizationId={}", organizationId);
        ctx.requireSameOrganization(organizationId);
        ctx.requireAdmin();

        Organization organization = getExistingOrganization(organizationId);
        organization.organizationName = request.organizationName;
        organization.address = request.address.toAddress();

        if (!organizationDao.updateOrganization(organization)) {
            throw organizationNotFound(organizationId);
        }

        Response response = new Response();
        response.organization = new ApiOrganization(organization);

        logger.debug("<updateOrganization() organizationId={}", organizationId);
        return response;
    }

    private Organization getExistingOrganization(int organizationId) {
        Organization organization = organizationDao.getOrganization(organizationId);
        if (organization == null) {
            throw organizationNotFound(organizationId);
        }
        return organization;
    }

    private ObjectNotFoundException organizationNotFound(int organizationId) {
        return new ObjectNotFoundException("Organization " + organizationId + " not found");
    }

    public static class UpdateOrganizationRequest {
        public @NotNull(message = "organizationName") String organizationName;
        public @Valid @NotNull(message = "address") ApiAddress address;
    }

    public static class Response extends ActionResponse {
        public ApiOrganization organization;
    }
}
