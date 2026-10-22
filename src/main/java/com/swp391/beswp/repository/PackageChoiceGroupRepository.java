package com.swp391.beswp.repository;

import com.swp391.beswp.entity.PackageChoiceGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PackageChoiceGroupRepository extends JpaRepository<PackageChoiceGroup, Integer> {

    @Query("SELECT g FROM PackageChoiceGroup g WHERE g.membershipPackage.id = :packageId ORDER BY g.sortOrder ASC, g.id ASC")
    List<PackageChoiceGroup> findByPackageId(@Param("packageId") Integer packageId);

    @Modifying
    @Query("DELETE FROM PackageChoiceGroup g WHERE g.membershipPackage.id = :packageId")
    void deleteByPackageId(@Param("packageId") Integer packageId);
}
