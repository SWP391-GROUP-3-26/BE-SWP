package com.swp391.beswp.repository;

import com.swp391.beswp.entity.PackageChoiceGroupSport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PackageChoiceGroupSportRepository extends JpaRepository<PackageChoiceGroupSport, Integer> {

    @Query("SELECT cgs FROM PackageChoiceGroupSport cgs JOIN FETCH cgs.subject WHERE cgs.choiceGroup.id = :groupId ORDER BY cgs.id ASC")
    List<PackageChoiceGroupSport> findByChoiceGroupId(@Param("groupId") Integer groupId);

    @Query("SELECT cgs FROM PackageChoiceGroupSport cgs JOIN FETCH cgs.subject WHERE cgs.choiceGroup.id IN :groupIds ORDER BY cgs.choiceGroup.id ASC, cgs.id ASC")
    List<PackageChoiceGroupSport> findByChoiceGroupIdIn(@Param("groupIds") List<Integer> groupIds);

    @Modifying
    @Query("DELETE FROM PackageChoiceGroupSport cgs WHERE cgs.choiceGroup.id = :groupId")
    void deleteByChoiceGroupId(@Param("groupId") Integer groupId);

    @Modifying
    @Query("DELETE FROM PackageChoiceGroupSport cgs WHERE cgs.choiceGroup.membershipPackage.id = :packageId")
    void deleteByPackageId(@Param("packageId") Integer packageId);

    boolean existsByChoiceGroup_IdAndSubject_Id(Integer groupId, Integer subjectId);
}
