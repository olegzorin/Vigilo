package dev.olegz.vf.core.domain.lambdarun.input;

public class LocationUserSnapshot {
    public int userId;
    public byte locationAccess;

    @Override
    public String toString() {
        return "{userId=" + userId +
            ", locationAccess=" + locationAccess +
            '}';
    }
}
