package com.swp391.beswp.repository;

import com.swp391.beswp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserManagementRepository extends JpaRepository<User, Integer>, JpaSpecificationExecutor<User> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.id = :id")
    java.util.Optional<User> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Integer id);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);

    boolean existsByUsernameIgnoreCaseAndIdNot(String username, Integer id);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Integer id);

    boolean existsByPhoneAndIdNot(String phone, Integer id);
}
