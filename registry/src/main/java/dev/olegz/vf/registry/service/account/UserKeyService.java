package dev.olegz.vf.registry.service.account;

import dev.olegz.vf.registry.domain.account.UserKeyJwtClaims;

public interface UserKeyService {

    /**
     * Parse and verify a user authentication key (a signed JWT), returning the claims it encodes.
     * @throws dev.olegz.vf.common.exception.InvalidJwtException if the key is missing,
     *         malformed, expired or has an invalid signature.
     */
    UserKeyJwtClaims parseUserKey(String key);

    /**
     * Mint a new general end-user authentication key (a signed JWT) for the given user. The key
     * encodes the user id and expires after
     * {@link dev.olegz.vf.common.props.DurationProp#USER_KEY_EXPIRY}. The returned string is
     * what callers send back in the {@code API_KEY} header and what
     * {@link #parseUserKey(String)} verifies.
     */
    String createUserKey(int userId);
}
