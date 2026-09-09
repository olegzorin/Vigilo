package dev.olegz.vf.registry.dao.impl;

import java.util.List;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.registry.dao.UserDao;
import dev.olegz.vf.registry.dao.mapper.UserMapper;
import dev.olegz.vf.registry.domain.account.User;
import org.springframework.stereotype.Repository;

@Repository("usersDao")
public class UserDaoImpl implements UserDao {
    private final UserMapper mapper;

    public UserDaoImpl(UserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertUser(User user) {
        if (user.createdAt == null) {
            user.createdAt = Datetime.now();
        }
        mapper.insertUser(user);
    }

    @Override
    public User getUser(int userId) {
        return mapper.selectUser(userId);
    }

    @Override
    public List<User> getUsers(
        Integer organizationId,
        Integer userId,
        String firstName,
        String lastName,
        String email)
    {
        return mapper.selectUsers(organizationId, userId, firstName, lastName, email);
    }

    @Override
    public List<User> getUsersByLocation(int locationId) {
        return mapper.selectUsersByLocation(locationId);
    }

    @Override
    public User getUserByUsername(String username) {
        return mapper.selectUserByUsername(username);
    }

    @Override
    public boolean updateUser(User user) {
        return mapper.updateUser(user);
    }

    @Override
    public boolean updatePassword(int userId, String password) {
        return mapper.updatePassword(userId, password);
    }

    @Override
    public boolean deleteUser(int userId) {
        return mapper.deleteUser(userId);
    }
}
