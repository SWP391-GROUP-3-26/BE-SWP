package com.swp391.beswp.repository;

import com.swp391.beswp.entity.ClassSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClassSessionRepository extends JpaRepository<ClassSession, Integer> {

    Optional<ClassSession> findByClassEntity_IdAndDateAndStartTime(Integer classId, LocalDate date, LocalTime startTime);

    @Query("SELECT s FROM ClassSession s " +
           "WHERE s.classEntity.id = :classId " +
           "AND LOWER(s.status) = 'scheduled' " +
           "AND s.date >= :currentDate " +
           "ORDER BY s.date ASC, s.startTime ASC")
    List<ClassSession> findUpcomingScheduledSessions(
            @Param("classId") Integer classId,
            @Param("currentDate") LocalDate currentDate
    );

    @Query("SELECT s FROM ClassSession s " +
           "WHERE s.classEntity.id = :classId " +
           "AND LOWER(s.status) NOT IN ('cancelled') " +
           "ORDER BY s.date DESC, s.startTime DESC")
    List<ClassSession> findActiveSessionsByClassId(@Param("classId") Integer classId);
}
