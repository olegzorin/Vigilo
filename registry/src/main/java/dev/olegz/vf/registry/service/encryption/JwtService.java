package dev.olegz.vf.registry.service.encryption;

/**
 * Creates and verifies typed JSON Web Tokens without depending on the consuming domain.
 */
public interface JwtService {
    String createJwt(JwtClaims claims, SigningAlgorithm algorithm);

    <T extends JwtClaims> T verifyJwt(String jwt, Class<T> claimsType);
}
