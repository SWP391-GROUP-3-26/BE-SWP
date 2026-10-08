package com.swp391.beswp.repository;

import com.swp391.beswp.entity.ClassEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClassRepository extends JpaRepository<ClassEntity, Integer> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);

    @Query("SELECT c FROM ClassEntity c " +
           "JOIN FETCH c.subject s " +
           "JOIN FETCH c.coach u " +
           "JOIN FETCH c.room r " +
           "WHERE (:status IS NULL OR LOWER(c.status) = LOWER(:status)) " +
           "AND (:keyword IS NULL OR " +
           "     LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     (:parsedId IS NOT NULL AND c.id = :parsedId)) " +
           "ORDER BY c.id DESC")
    List<ClassEntity> searchClasses(
            @Param("keyword") String keyword,
            @Param("parsedId") Integer parsedId,
            @Param("status") String status
    );

    @Query("SELECT c FROM ClassEntity c " +
           "WHERE c.room.id = :roomId " +
           "AND LOWER(c.status) NOT IN ('cancelled', 'cancel')")
    List<ClassEntity> findActiveClassesByRoomId(@Param("roomId") Integer roomId);

    @Query("SELECT c FROM ClassEntity c " +
           "WHERE c.coach.id = :coachId " +
           "AND LOWER(c.status) NOT IN ('cancelled', 'cancel')")
    List<ClassEntity> findActiveClassesByCoachId(@Param("coachId") Integer coachId);
}
