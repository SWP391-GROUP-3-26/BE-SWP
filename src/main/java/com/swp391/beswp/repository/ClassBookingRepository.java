package com.swp391.beswp.repository;

import com.swp391.beswp.entity.ClassBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClassBookingRepository extends JpaRepository<ClassBooking, Integer> {

    @Query("SELECT b FROM ClassBooking b " +
           "JOIN FETCH b.session s " +
           "JOIN FETCH s.classEntity c " +
           "JOIN FETCH c.room r " +
           "JOIN FETCH c.coach co " +
           "JOIN FETCH c.subject sub " +
           "WHERE b.user.id = :userId " +
           "AND (:status IS NULL OR LOWER(b.status) = LOWER(:status)) " +
           "AND (:keyword IS NULL OR " +
           "     LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(co.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY b.bookingDatetime DESC")
    List<ClassBooking> findMyBookings(
            @Param("userId") Integer userId,
            @Param("status") String status,
            @Param("keyword") String keyword
    );

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN TRUE ELSE FALSE END FROM ClassBooking b " +
           "WHERE b.user.id = :userId " +
           "AND b.session.classEntity.id = :classId " +
           "AND LOWER(b.status) = 'booked'")
    boolean existsActiveBookingByUserIdAndClassId(
            @Param("userId") Integer userId,
            @Param("classId") Integer classId
    );

    @Query("SELECT COUNT(DISTINCT b.user.id) FROM ClassBooking b " +
           "WHERE b.session.classEntity.id = :classId " +
           "AND LOWER(b.status) = 'booked'")
    long countActiveMembersByClassId(@Param("classId") Integer classId);

    @Query("SELECT COUNT(b) FROM ClassBooking b " +
           "WHERE b.user.id = :userId " +
           "AND LOWER(b.status) = LOWER(:status)")
    long countByUserIdAndStatus(
            @Param("userId") Integer userId,
            @Param("status") String status
    );

    long countByUser_Id(Integer userId);

    @Query("SELECT COUNT(b) FROM ClassBooking b " +
           "WHERE b.user.id = :userId " +
           "AND LOWER(b.status) IN ('booked', 'completed') " +
           "AND b.bookingDatetime >= :start " +
           "AND b.bookingDatetime <= :end " +
           "AND b.id NOT IN (SELECT p.booking.id FROM Payment p WHERE p.booking IS NOT NULL AND LOWER(p.status) = 'paid')")
    long countUsedPackageSessions(
            @Param("userId") Integer userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    Optional<ClassBooking> findByIdAndUser_Id(Integer bookingId, Integer userId);
}
