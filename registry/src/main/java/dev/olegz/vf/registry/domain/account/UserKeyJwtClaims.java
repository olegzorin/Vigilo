package dev.olegz.vf.registry.domain.account;

import dev.olegz.vf.registry.service.encryption.JwtClaims;

public class UserKeyJwtClaims extends JwtClaims {
    public int uid; // user ID

    public UserKeyJwtClaims() {
    }

    public UserKeyJwtClaims(long exp, int userId) {
        this.ty = JwtClaims.TYPE_USER;
        this.exp = exp;
        this.uid = userId;
    }

    @Override
    public boolean valid() {
        return (ty == JwtClaims.TYPE_USER) && (uid != 0);
    }
}
