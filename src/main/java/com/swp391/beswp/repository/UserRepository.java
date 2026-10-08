package com.swp391.beswp.repository;

import com.swp391.beswp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {

    @Query("""
            select u from User u join u.role r
            where lower(r.roleName) = 'member' and (
                locate(lower(:keyword), lower(u.fullName)) > 0 or
                locate(lower(:keyword), lower(u.username)) > 0 or
                locate(lower(:keyword), lower(u.email)) > 0 or
                locate(lower(:keyword), lower(u.phone)) > 0)
            """)
    Page<User> searchMembers(@Param("keyword") String keyword, Pageable pageable);

    Optional<User> findByIdAndRoleRoleNameIgnoreCase(Integer id, String roleName);

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);

    java.util.List<User> findByRole_RoleNameIgnoreCase(String roleName);

    java.util.List<User> findByRole_RoleNameIgnoreCaseAndStatusIgnoreCase(String roleName, String status);
}
