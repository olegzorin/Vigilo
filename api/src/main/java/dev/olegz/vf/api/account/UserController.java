package dev.olegz.vf.api.account;

import dev.olegz.vf.api.web.support.ActionContextFactory;
import dev.olegz.vf.api.web.support.ActionResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import static dev.olegz.vf.api.web.ApiHeaders.API_KEY;

@RestController("userController")
@RequestMapping(path = "/vf/users", produces = MediaType.APPLICATION_JSON_VALUE)
public class UserController {

    private final UserAction userAction;
    private final ActionContextFactory contextFactory;

    public UserController(UserAction userAction, ActionContextFactory contextFactory) {
        this.userAction = userAction;
        this.contextFactory = contextFactory;
    }

    @RequestMapping(method = RequestMethod.GET)
    public ActionResponse getUsers(
        @RequestHeader(API_KEY) String key,
        @RequestParam(required = false) String firstName,
        @RequestParam(required = false) String lastName,
        @RequestParam(required = false) String email)
    {
        return userAction.getUsers(contextFactory.current(), firstName, lastName, email);
    }

    @RequestMapping(method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ActionResponse createUser(
        @RequestHeader(API_KEY) String key,
        @Valid @RequestBody UserAction.CreateUserRequest request)
    {
        return userAction.createUser(contextFactory.current(), request);
    }

    @RequestMapping(method = RequestMethod.POST, path = "authenticate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public UserAction.AuthenticationResponse authenticate(
        @Valid @RequestBody UserAction.AuthenticateRequest request)
    {
        return userAction.authenticate(request);
    }

    @RequestMapping(method = RequestMethod.PUT, path = "{userId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ActionResponse updateProfile(
        @RequestHeader(API_KEY) String key,
        @PathVariable int userId,
        @Valid @RequestBody UserAction.UpdateProfileRequest request)
    {
        return userAction.updateProfile(contextFactory.current(), userId, request);
    }

    @RequestMapping(method = RequestMethod.PUT, path = "{userId}/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ActionResponse changePassword(
        @RequestHeader(API_KEY) String key,
        @PathVariable int userId,
        @Valid @RequestBody UserAction.ChangePasswordRequest request)
    {
        return userAction.changePassword(contextFactory.current(), userId, request);
    }
}
