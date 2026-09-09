package dev.olegz.vf.registry.service.encryption;

public class JwtClaims {
    public static final byte TYPE_USER = 0;
    public static final byte TYPE_LAMBDA = 1;
    public static final byte TYPE_REPORT = 2;
    public static final byte TYPE_TICKET = 3;
    public static final byte TYPE_SURVEY = 4;
    public static final byte TYPE_VAYYAR_DEVICE = 5;
    public static final byte TYPE_VAYYAR_SIGNATURE = 6;
    public static final byte TYPE_LOCATION = 7;

    // standard claims
    public byte ty;     // token type
    public String iss;  // issuer
    public long exp;    // expiration in seconds

    public boolean valid() {
        return true;
    }
}
