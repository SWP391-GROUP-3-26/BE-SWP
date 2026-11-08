package com.swp391.beswp.repository;

import com.swp391.beswp.entity.ClassEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClassRepository extends JpaRepository<ClassEntity, Integer> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ClassEntity c WHERE c.id = :id")
    java.util.Optional<ClassEntity> findByIdForUpdate(@Param("id") Integer id);

    @Query("SELECT c.id FROM ClassEntity c WHERE c.endDate < :today AND LOWER(c.status) IN ('active', 'open') ORDER BY c.id")
    List<Integer> findExpiredClassIds(@Param("today") java.time.LocalDate today);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);

    boolean existsByRoom_Id(Integer roomId);

    @Query("SELECT c FROM ClassEntity c JOIN FETCH c.room r " +
            "WHERE r.id = :roomId AND LOWER(c.status) NOT IN ('cancelled', 'cancel', 'complete', 'completed') " +
            "AND (EXISTS (SELECT cs.id FROM ClassSession cs WHERE cs.classEntity.id = c.id AND LOWER(cs.status) = 'scheduled' " +
            "AND (c.date IS NULL OR cs.date >= c.date) AND (c.endDate IS NULL OR cs.date <= c.endDate) " +
            "AND (cs.date > CAST(:now AS date) OR (cs.date = CAST(:now AS date) AND cs.endTime > CAST(:now AS time)))) " +
            "OR (NOT EXISTS (SELECT cs.id FROM ClassSession cs WHERE cs.classEntity.id = c.id) " +
            "AND (c.date > CAST(:now AS date) OR (c.date = CAST(:now AS date) " +
            "AND COALESCE(c.endTime, c.startTime, c.time) > CAST(:now AS time))))) ORDER BY c.id")
    List<ClassEntity> findUpcomingClassesByRoomId(@Param("roomId") Integer roomId,
                                                @Param("now") java.time.LocalDateTime now);

    @Query("SELECT DISTINCT c.room.id FROM ClassEntity c WHERE c.subject.id = :subjectId ORDER BY c.room.id")
    List<Integer> findRoomIdsBySubjectId(@Param("subjectId") Integer subjectId);

    @Query("SELECT DISTINCT c.subject.id FROM ClassEntity c WHERE c.room.id = :roomId ORDER BY c.subject.id")
    List<Integer> findSubjectIdsByRoomId(@Param("roomId") Integer roomId);

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
           "AND LOWER(c.status) NOT IN ('cancelled', 'cancel', 'complete', 'completed')")
    List<ClassEntity> findActiveClassesByRoomId(@Param("roomId") Integer roomId);

    @Query("SELECT c FROM ClassEntity c " +
           "WHERE c.coach.id = :coachId " +
           "AND LOWER(c.status) NOT IN ('cancelled', 'cancel', 'complete', 'completed')")
    List<ClassEntity> findActiveClassesByCoachId(@Param("coachId") Integer coachId);

    @Query("SELECT c FROM ClassEntity c " +
           "JOIN FETCH c.subject s " +
           "JOIN FETCH c.coach u " +
           "JOIN FETCH c.room r " +
           "WHERE c.coach.id = :coachId " +
           "AND (:status IS NULL OR LOWER(c.status) = LOWER(:status)) " +
           "AND (:keyword IS NULL OR " +
           "     LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     (:parsedId IS NOT NULL AND c.id = :parsedId)) " +
           "ORDER BY c.id DESC")
    List<ClassEntity> findClassesByCoach(
            @Param("coachId") Integer coachId,
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("parsedId") Integer parsedId
    );

    long countByCoach_Id(Integer coachId);

    @Query("SELECT COUNT(c) FROM ClassEntity c " +
           "WHERE c.coach.id = :coachId " +
           "AND LOWER(c.status) IN ('active', 'open')")
    long countActiveClassesByCoachId(@Param("coachId") Integer coachId);

    @Query("SELECT COUNT(c) FROM ClassEntity c " +
           "WHERE c.coach.id = :coachId " +
           "AND LOWER(c.status) IN ('complete', 'completed')")
    long countCompletedClassesByCoachId(@Param("coachId") Integer coachId);

    @Query("SELECT COUNT(c) FROM ClassEntity c " +
           "WHERE c.coach.id = :coachId " +
           "AND LOWER(c.status) IN ('cancel', 'cancelled')")
    long countCancelledClassesByCoachId(@Param("coachId") Integer coachId);
}
