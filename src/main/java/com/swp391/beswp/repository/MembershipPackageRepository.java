package com.swp391.beswp.repository;

import com.swp391.beswp.entity.MembershipPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MembershipPackageRepository extends JpaRepository<MembershipPackage, Integer> {

    boolean existsByNameIgnoreCase(String name);

    /**
     * Tìm kiếm package theo tên, mô tả, mã PKG, hoặc lọc theo trạng thái.
     * Nếu keyword null → lấy tất cả. Nếu status null → không lọc theo status.
     */
    @Query("""
            SELECT p FROM MembershipPackage p
            WHERE (:keyword IS NULL
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR LOWER(p.status) = LOWER(:status))
            ORDER BY p.id ASC
            """)
    List<MembershipPackage> searchPackages(
            @Param("keyword") String keyword,
            @Param("status") String status
    );
}
