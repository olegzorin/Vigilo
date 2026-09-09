package dev.olegz.vf.registry.service.account;

import java.time.Instant;

import dev.olegz.vf.common.exception.InvalidJwtException;
import dev.olegz.vf.common.props.DurationProp;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.registry.domain.account.UserKeyJwtClaims;
import dev.olegz.vf.registry.service.encryption.JwtService;
import dev.olegz.vf.registry.service.encryption.SigningAlgorithm;
import org.springframework.stereotype.Service;

@Service("userKeyService")
public class UserKeyServiceImpl implements UserKeyService {

    private final JwtService jwtService;

    public UserKeyServiceImpl(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public UserKeyJwtClaims parseUserKey(String key) {
        if (key == null || key.isBlank()) throw new InvalidJwtException();
        return jwtService.verifyJwt(key, UserKeyJwtClaims.class);
    }

    @Override
    public String createUserKey(int userId) {
        long exp = Instant.now().getEpochSecond()
            + PropertyStore.getDuration(DurationProp.USER_KEY_EXPIRY).toSeconds();
        UserKeyJwtClaims claims = new UserKeyJwtClaims(exp, userId);
        return jwtService.createJwt(claims, SigningAlgorithm.ED25519);
    }
}
