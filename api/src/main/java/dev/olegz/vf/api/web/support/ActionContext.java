package dev.olegz.vf.api.web.support;

import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.registry.dao.OrganizationDao;
import dev.olegz.vf.registry.domain.account.Organization;
import dev.olegz.vf.registry.domain.account.User;

/**
 * Immutable per-request context handed to the stateless Action singletons.
 * <p>
 * It carries everything an action needs to know about the calling request
 * (identity and whether the key is administrative). Previously this
 * state lived in mutable fields on a per-request {@code BasicAction} prototype
 * instance; moving it into a value object that is passed as a method argument
 * lets the actions themselves be shared singletons.
 * <p>
 * Instances are produced by {@link ActionContextFactory}, which injects the
 * collaborators this context needs.
 */
public final class ActionContext {

    /** End user. !!! Do not modify this user object. It is in cache memory. */
    private final User user;

    private final OrganizationDao organizationDao;

    public ActionContext(User user, OrganizationDao organizationDao) {
        this.user = user;
        this.organizationDao = organizationDao;
    }

    /** The end user. */
    public User user() {
        if (user != null) return user;
        throw new AccessDeniedException("Request related user not found");
    }

    /** Id of the end user. */
    public int userId() {
        return user().userId;
    }

    /**
     * Require that the caller is acting on their own account.
     * @throws AccessDeniedException if the caller is unauthenticated or targeting another user.
     */
    public void requireSelf(int targetUserId) {
        if (userId() != targetUserId) {
            throw new AccessDeniedException("Access to user " + targetUserId + " denied");
        }
    }

    /**
     * Require that the caller is the administrator of their organization, i.e. the user the
     * organization designates via {@code admin_user_id}.
     * @throws AccessDeniedException if the caller is unauthenticated or is not their organization's admin.
     */
    public void requireAdmin() {
        if (!isAdmin()) {
            throw new AccessDeniedException("Administrator privileges required");
        }
    }

    /** @return whether the caller is the administrator of their organization. */
    public boolean isAdmin() {
        Organization org = organizationDao.getOrganization(user().organizationId);
        return org != null && org.adminUserId != null && org.adminUserId == user().userId;
    }

    /**
     * Require that the caller belongs to the given organization.
     * @throws AccessDeniedException if the caller is unauthenticated or in a different organization.
     */
    public void requireSameOrganization(int organizationId) {
        if (user().organizationId != organizationId) {
            throw new AccessDeniedException("Access to organization " + organizationId + " denied");
        }
    }
}
