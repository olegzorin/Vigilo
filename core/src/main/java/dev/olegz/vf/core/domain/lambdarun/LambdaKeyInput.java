package dev.olegz.vf.core.domain.lambdarun;

public class LambdaKeyInput {
    public final String key;
    public final long expiry;

    public LambdaKeyInput(String key, long expiry) {
        this.key = key;
        this.expiry = expiry;
    }
}
