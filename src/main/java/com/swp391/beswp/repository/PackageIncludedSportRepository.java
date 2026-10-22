package com.swp391.beswp.repository;

import com.swp391.beswp.entity.PackageIncludedSport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PackageIncludedSportRepository extends JpaRepository<PackageIncludedSport, Integer> {

    @Query("SELECT pis FROM PackageIncludedSport pis JOIN FETCH pis.subject WHERE pis.membershipPackage.id = :packageId ORDER BY pis.id ASC")
    List<PackageIncludedSport> findByPackageId(@Param("packageId") Integer packageId);

    @Modifying
    @Query("DELETE FROM PackageIncludedSport pis WHERE pis.membershipPackage.id = :packageId")
    void deleteByPackageId(@Param("packageId") Integer packageId);

    boolean existsByMembershipPackage_IdAndSubject_Id(Integer packageId, Integer subjectId);
}
