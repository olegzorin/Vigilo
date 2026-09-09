package dev.olegz.vf.aws.local;

import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.*;

/**
 * Local synthetic implementation of {@link IamClient}. Returns deterministic ARNs built from the
 * synthetic account id {@link LocalAws#ACCOUNT_ID}, satisfying
 * {@code dev.olegz.vf.aws.iam.IamSupport} (execution-role ARN lookup and account-id
 * extraction).
 */
public final class LocalIamClient implements IamClient {

    @Override
    public GetRoleResponse getRole(GetRoleRequest request) {
        String roleName = request.roleName();
        return GetRoleResponse.builder()
            .role(Role.builder()
                .roleName(roleName)
                .arn("arn:aws:iam::" + LocalAws.ACCOUNT_ID + ":role/" + roleName)
                .build())
            .build();
    }

    @Override
    public GetUserResponse getUser(GetUserRequest request) {
        return GetUserResponse.builder()
            .user(User.builder().arn("arn:aws:iam::" + LocalAws.ACCOUNT_ID + ":user/local").build())
            .build();
    }

    @Override
    public String serviceName() {
        return IamClient.SERVICE_NAME;
    }

    @Override
    public void close() {
    }
}
