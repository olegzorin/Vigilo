package dev.olegz.vf.registry.dao.mapper;

import java.util.List;

import dev.olegz.vf.registry.service.encryption.SigningKey;

public interface SigningKeyMapper {
	List<SigningKey> selectSigningKeys();
	void insertSigningKey(SigningKey key);
}
