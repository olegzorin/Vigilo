package dev.olegz.vf.registry.service.encryption;

import dev.olegz.vf.common.Datetime;

/**
 * A value object for a signing key stored in the database.
 */
public class SigningKey {
    public static final byte STATUS_ACTIVE = 1;
    public static final byte STATUS_DEACTIVATED = 2;
    //public static final byte STATUS_ROTATED = 3;

    public int keyId;
    public SigningAlgorithm algorithm;
    public byte status;
    public byte[] encryptedKey;
    public byte[] publicKey;
    public Datetime createdAt;

    @Override
    public String toString() {
        return "{keyId=" + keyId +
            ", algorithm=" + algorithm +
            (status != STATUS_ACTIVE ? ", status=" + status : "") +
            '}';
    }

    public boolean active(long inactiveTime) {
        return (STATUS_ACTIVE == status) && (createdAt.getTime() <= inactiveTime);
    }
}
