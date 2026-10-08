package com.swp391.beswp.repository;

import com.swp391.beswp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);

    java.util.List<User> findByRole_RoleNameIgnoreCase(String roleName);

    java.util.List<User> findByRole_RoleNameIgnoreCaseAndStatusIgnoreCase(String roleName, String status);
}
