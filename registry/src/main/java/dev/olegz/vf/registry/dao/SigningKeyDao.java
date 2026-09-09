package dev.olegz.vf.registry.dao;

import java.util.List;

import dev.olegz.vf.registry.service.encryption.SigningKey;

public interface SigningKeyDao {
    List<SigningKey> getSigningKeys();

    void insertSigningKey(SigningKey key);
}
