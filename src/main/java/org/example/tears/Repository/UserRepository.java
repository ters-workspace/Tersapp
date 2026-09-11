package org.example.tears.Repository;

import org.example.tears.Enums.UserRole;
import org.example.tears.Enums.UserStatus;
import org.example.tears.Model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
public interface UserRepository extends JpaRepository<User,Integer> {

    void deleteByPhoneNumber(String phoneNumber);

    Optional<User> findByPhoneNumber(String phone);

    Optional<User> findByEmailOrPhoneNumber(
            String email,
            String phoneNumber
    );

    Optional<User> findByEmail(String email);

    List<User> findByRole(UserRole role);

    boolean existsByPhoneNumber(String phone);

    long countByRoleAndStatus(
            UserRole role,
            UserStatus status
    );
}
