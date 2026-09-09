package dev.olegz.vf.registry.dao.mapper;

import java.util.List;

import dev.olegz.vf.registry.domain.account.User;
import org.apache.ibatis.annotations.Param;

public interface UserMapper {

    void insertUser(User user);

    User selectUser(int userId);

    List<User> selectUsers(
        @Param("organizationId") Integer organizationId,
        @Param("userId") Integer userId,
        @Param("firstName") String firstName,
        @Param("lastName") String lastName,
        @Param("email") String email);

    List<User> selectUsersByLocation(int locationId);

    User selectUserByUsername(String username);

    boolean updateUser(User user);

    boolean updatePassword(
        @Param("userId") int userId,
        @Param("password") String password);

    boolean deleteUser(int userId);

}
