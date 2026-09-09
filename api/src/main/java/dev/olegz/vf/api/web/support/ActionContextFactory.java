package dev.olegz.vf.api.web.support;

import dev.olegz.vf.api.web.ApiHeaders;
import dev.olegz.vf.api.web.filter.LocalRequestContext;
import dev.olegz.vf.common.exception.InvalidJwtException;
import dev.olegz.vf.registry.dao.OrganizationDao;
import dev.olegz.vf.registry.domain.account.User;
import dev.olegz.vf.registry.domain.account.UserKeyJwtClaims;
import dev.olegz.vf.registry.service.account.UserKeyService;
import dev.olegz.vf.registry.service.account.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Builds the per-request {@link ActionContext}. A Spring-managed singleton so the
 * collaborators needed to resolve the caller are injected rather than looked up.
 */
@Component
public class ActionContextFactory {

    private final UserKeyService userKeyService;
    private final UserService userService;
    private final OrganizationDao organizationDao;

    public ActionContextFactory(UserKeyService userKeyService,
                                UserService userService,
                                OrganizationDao organizationDao) {
        this.userKeyService = userKeyService;
        this.userService = userService;
        this.organizationDao = organizationDao;
    }

    /**
     * Build the context for the request currently bound to this thread, resolving the caller
     * from the {@code API_KEY} header.
     * <ul>
     *   <li>No {@code API_KEY} header: an unauthenticated context. Public endpoints (e.g. login)
     *       work; any action that needs identity fails via {@link ActionContext#user()}/
     *       {@link ActionContext#userId()}.</li>
     *   <li>A valid user key: the context carries the resolved user.</li>
     *   <li>A malformed/expired/unknown key: {@link InvalidJwtException} (HTTP 401).</li>
     * </ul>
     */
    public ActionContext current() {
        HttpServletRequest request = LocalRequestContext.getRequest();
        String apiKey = request != null ? request.getHeader(ApiHeaders.API_KEY) : null;
        if (apiKey == null || apiKey.isBlank()) {
            return new ActionContext(null, organizationDao);
        }

        UserKeyJwtClaims userKey = userKeyService.parseUserKey(apiKey);
        User user = userService.getUser(userKey.uid);
        if (user == null) throw new InvalidJwtException();

        return new ActionContext(user, organizationDao);
    }
}
