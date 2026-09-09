package dev.olegz.vf.api.account;

import java.util.List;

import dev.olegz.vf.api.location.ApiLocation;
import dev.olegz.vf.api.web.support.ActionContext;
import dev.olegz.vf.api.web.support.ActionResponse;
import dev.olegz.vf.common.exception.ObjectNotFoundException;
import dev.olegz.vf.common.util.CollectionOps;
import dev.olegz.vf.registry.dao.LocationDao;
import dev.olegz.vf.registry.domain.account.Location;
import dev.olegz.vf.registry.domain.account.User;
import dev.olegz.vf.registry.service.account.UserKeyService;
import dev.olegz.vf.registry.service.account.UserService;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UserAction {
    private static final Logger logger = LoggerFactory.getLogger(UserAction.class);

    private final UserService userService;
    private final UserKeyService userKeyService;
    private final LocationDao locationDao;

    public UserAction(UserService userService, UserKeyService userKeyService, LocationDao locationDao) {
        this.userService = userService;
        this.userKeyService = userKeyService;
        this.locationDao = locationDao;
    }

    public Response createUser(ActionContext ctx, CreateUserRequest request) {
        logger.debug(">createUser() username={}", request.username);

        // Only an admin may create users, and only non-admin users within their own organization.
        ctx.requireAdmin();
        ctx.requireSameOrganization(request.organizationId);

        User user = new User();
        user.username = request.username;
        user.firstName = request.firstName;
        user.lastName = request.lastName;
        user.email = request.email;
        user.phone = request.phone;
        user.organizationId = request.organizationId;
        // The new user is never made an org admin here: an organization's admin is set via its
        // admin_user_id, which this endpoint never touches. So admins can only create non-admins.
        userService.createUser(user, request.password);

        Response response = new Response();
        response.user = new ApiUser(user);

        logger.debug("<createUser() userId={}", user.userId);
        return response;
    }

    public AuthenticationResponse authenticate(AuthenticateRequest request) {
        logger.debug(">authenticate() username={}", request.username);

        User user = userService.authenticate(request.username, request.password);

        AuthenticationResponse response = new AuthenticationResponse();
        response.user = new ApiUser(user);
        response.location = toApiLocation(locationDao.getLocationByUser(user));
        response.apiKey = userKeyService.createUserKey(user.userId);

        logger.debug("<authenticate() userId={}", user.userId);
        return response;
    }

    public UsersResponse getUsers(ActionContext ctx, String firstName, String lastName, String email) {
        User caller = ctx.user();
        boolean admin = ctx.isAdmin();
        logger.debug(">getUsers() userId={}, admin={}", caller.userId, admin);

        List<User> users = userService.getUsers(
            admin ? caller.organizationId : null,
            admin ? null : caller.userId,
            firstName,
            lastName,
            email);

        UsersResponse response = new UsersResponse();
        response.users = CollectionOps.map(users, ApiUser::new);
        response.collectionTotalSize = response.users.size();

        logger.debug("<getUsers() count={}", response.users.size());
        return response;
    }

    public Response updateProfile(ActionContext ctx, int userId, UpdateProfileRequest request) {
        logger.debug(">updateProfile() userId={}", userId);
        ctx.requireSelf(userId);

        User user = new User();
        user.userId = userId;
        user.firstName = request.firstName;
        user.lastName = request.lastName;
        user.email = request.email;
        user.phone = request.phone;

        User updated = userService.updateProfile(user);
        if (updated == null) {
            throw new ObjectNotFoundException("User " + userId + " not found");
        }

        Response response = new Response();
        response.user = new ApiUser(updated);

        logger.debug("<updateProfile() userId={}", userId);
        return response;
    }

    public ActionResponse changePassword(ActionContext ctx, int userId, ChangePasswordRequest request) {
        logger.debug(">changePassword() userId={}", userId);
        ctx.requireSelf(userId);

        if (!userService.changePassword(userId, request.password)) {
            throw new ObjectNotFoundException("User " + userId + " not found");
        }

        logger.debug("<changePassword() userId={}", userId);
        return new ActionResponse();
    }

    public static class CreateUserRequest {
        public @NotNull(message = "username") String username;
        public @NotNull(message = "password") String password;
        public String firstName;
        public String lastName;
        public String email;
        public String phone;
        public int organizationId;
    }

    public static class AuthenticateRequest {
        public @NotNull(message = "username") String username;
        public @NotNull(message = "password") String password;
    }

    public static class UpdateProfileRequest {
        public String firstName;
        public String lastName;
        public String email;
        public String phone;
    }

    public static class ChangePasswordRequest {
        public @NotNull(message = "password") String password;
    }

    public static class Response extends ActionResponse {
        public ApiUser user;
    }

    public static class UsersResponse extends ActionResponse {
        public List<ApiUser> users;
    }

    public static class AuthenticationResponse extends Response {
        public String apiKey;
        public ApiLocation location;
    }

    private static ApiLocation toApiLocation(Location location) {
        return location == null ? null : new ApiLocation(location);
    }
}
