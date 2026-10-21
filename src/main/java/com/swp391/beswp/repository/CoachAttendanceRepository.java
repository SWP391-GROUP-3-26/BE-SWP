package com.swp391.beswp.repository;

import com.swp391.beswp.entity.AttendanceRecord;
import com.swp391.beswp.entity.ClassBooking;
import com.swp391.beswp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CoachAttendanceRepository extends JpaRepository<AttendanceRecord, Integer> {

    List<AttendanceRecord> findBySession_Id(Integer sessionId);

    List<AttendanceRecord> findBySession_IdIn(Collection<Integer> sessionIds);

    @Query("SELECT b FROM ClassBooking b " +
            "JOIN FETCH b.session s " +
            "JOIN FETCH b.user u " +
            "JOIN FETCH u.role r " +
            "WHERE s.classEntity.id = :classId " +
            "AND LOWER(b.status) IN ('booked', 'completed') " +
            "AND LOWER(r.roleName) = 'member'")
    List<ClassBooking> findEnrolledBookingsForClass(@Param("classId") Integer classId);

    @Query("SELECT DISTINCT u FROM ClassBooking b " +
            "JOIN b.user u " +
            "JOIN u.role r " +
            "WHERE b.session.id = :sessionId " +
            "AND LOWER(b.status) IN ('booked', 'completed') " +
            "AND LOWER(r.roleName) = 'member' " +
            "ORDER BY u.fullName ASC, u.id ASC")
    List<User> findRegisteredMembersForSession(@Param("sessionId") Integer sessionId);
}
