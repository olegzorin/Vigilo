package dev.olegz.vf.registry.dao.impl;

import java.util.List;

import dev.olegz.vf.registry.dao.SigningKeyDao;
import dev.olegz.vf.registry.dao.mapper.SigningKeyMapper;
import dev.olegz.vf.registry.service.encryption.SigningKey;
import org.springframework.stereotype.Repository;

@Repository
public class SigningKeyDaoImpl implements SigningKeyDao {
    private final SigningKeyMapper mapper;

	public SigningKeyDaoImpl(SigningKeyMapper mapper) {
		this.mapper = mapper;
	}

	@Override
	public List<SigningKey> getSigningKeys() {
		return mapper.selectSigningKeys();
	}

	@Override
	public void insertSigningKey(SigningKey key) {
		mapper.insertSigningKey(key);
	}
}
