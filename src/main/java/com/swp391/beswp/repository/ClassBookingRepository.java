package com.swp391.beswp.repository;

import com.swp391.beswp.entity.ClassBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClassBookingRepository extends JpaRepository<ClassBooking, Integer> {

    List<ClassBooking> findBySession_ClassEntity_Id(Integer classId);

    List<ClassBooking> findByRegistrationCodeOrderByIdAsc(String registrationCode);

    @Query("SELECT COUNT(DISTINCT b.user.id) FROM ClassBooking b WHERE b.session.id = :sessionId " +
           "AND (LOWER(b.status) = 'booked' OR (LOWER(b.status) = 'pending' AND b.paymentExpiresAt > :now))")
    long countReservedMembersBySessionId(@Param("sessionId") Integer sessionId, @Param("now") LocalDateTime now);

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
           "AND (b.session.date > CAST(:now AS date) OR (b.session.date = CAST(:now AS date) AND b.session.startTime > CAST(:now AS time))) " +
           "AND (LOWER(b.status) = 'booked' OR (LOWER(b.status) = 'pending' AND b.paymentExpiresAt > :now))")
    boolean existsActiveBookingByUserIdAndClassId(
            @Param("userId") Integer userId,
            @Param("classId") Integer classId,
            @Param("now") LocalDateTime now
    );

    @Query("SELECT COUNT(DISTINCT b.user.id) FROM ClassBooking b " +
           "WHERE b.session.classEntity.id = :classId " +
           "AND LOWER(b.status) = 'booked'")
    long countActiveMembersByClassId(@Param("classId") Integer classId);

    @Query("SELECT COUNT(DISTINCT b.user.id) FROM ClassBooking b WHERE b.session.classEntity.id = :classId " +
           "AND (LOWER(b.status) = 'booked' OR (LOWER(b.status) = 'pending' AND b.paymentExpiresAt > :now))")
    long countReservedMembersByClassId(@Param("classId") Integer classId, @Param("now") LocalDateTime now);

    Optional<ClassBooking> findByUser_IdAndSession_Id(Integer userId, Integer sessionId);

    @Query("SELECT b FROM ClassBooking b WHERE b.session.classEntity.id = :classId AND b.status = 'Pending' AND b.paymentExpiresAt <= :now")
    List<ClassBooking> findExpiredByClassId(@Param("classId") Integer classId, @Param("now") LocalDateTime now);

    @Query("SELECT b.session.classEntity.id FROM ClassBooking b WHERE b.id = :id AND b.user.id = :userId")
    Optional<Integer> findOwnedClassId(@Param("id") Integer id, @Param("userId") Integer userId);

    @Query("SELECT COUNT(b) FROM ClassBooking b " +
           "WHERE b.user.id = :userId " +
           "AND LOWER(b.status) = LOWER(:status)")
    long countByUserIdAndStatus(
            @Param("userId") Integer userId,
            @Param("status") String status
    );

    @Query("SELECT COUNT(b) FROM ClassBooking b WHERE b.user.id = :userId AND LOWER(b.status) <> 'pending'")
    long countByUser_Id(@Param("userId") Integer userId);

    @Query("SELECT COUNT(b) FROM ClassBooking b " +
           "WHERE b.user.id = :userId " +
           "AND LOWER(b.status) IN ('booked', 'completed') " +
           "AND b.bookingDatetime >= :start " +
           "AND b.bookingDatetime <= :end " +
           "AND (b.paymentMethod = 'PACKAGE' OR (b.paymentMethod IS NULL AND " +
           "b.id NOT IN (SELECT p.booking.id FROM Payment p WHERE p.booking IS NOT NULL AND LOWER(p.status) = 'paid')))")
    long countUsedPackageSessions(
            @Param("userId") Integer userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    Optional<ClassBooking> findByIdAndUser_Id(Integer bookingId, Integer userId);

    @Query("SELECT b FROM ClassBooking b " +
           "JOIN FETCH b.user u " +
           "WHERE b.session.classEntity.id = :classId " +
           "AND LOWER(b.status) IN ('booked', 'completed') " +
           "ORDER BY b.bookingDatetime ASC")
    List<ClassBooking> findEnrolledStudentsByClassId(@Param("classId") Integer classId);

    @Query("SELECT COUNT(DISTINCT b.user.id) FROM ClassBooking b " +
           "WHERE b.session.classEntity.coach.id = :coachId " +
           "AND LOWER(b.status) = 'booked' " +
           "AND LOWER(b.session.classEntity.status) IN ('active', 'open') " +
           "AND (b.session.classEntity.endDate IS NULL OR b.session.classEntity.endDate >= :today)")
    long countTotalActiveStudentsByCoachId(@Param("coachId") Integer coachId, @Param("today") java.time.LocalDate today);

    @Query("SELECT b FROM ClassBooking b " +
           "JOIN FETCH b.user member " +
           "JOIN FETCH b.session s " +
           "JOIN FETCH s.classEntity c " +
           "JOIN FETCH c.coach coach " +
           "JOIN FETCH c.subject subject " +
           "WHERE member.id = :memberId " +
           "AND LOWER(b.status) = 'booked' " +
           "AND LOWER(c.status) IN ('active', 'open') " +
           "AND (c.endDate IS NULL OR c.endDate >= :today) " +
           "ORDER BY s.date DESC, b.id DESC")
    List<ClassBooking> findChatBookingsForMember(
            @Param("memberId") Integer memberId,
            @Param("today") LocalDate today
    );

    @Query("SELECT b FROM ClassBooking b " +
           "JOIN FETCH b.user member " +
           "JOIN FETCH b.session s " +
           "JOIN FETCH s.classEntity c " +
           "JOIN FETCH c.coach coach " +
           "JOIN FETCH c.subject subject " +
           "WHERE coach.id = :coachId " +
           "AND LOWER(b.status) = 'booked' " +
           "AND LOWER(c.status) IN ('active', 'open') " +
           "AND (c.endDate IS NULL OR c.endDate >= :today) " +
           "ORDER BY s.date DESC, b.id DESC")
    List<ClassBooking> findChatBookingsForCoach(
            @Param("coachId") Integer coachId,
            @Param("today") LocalDate today
    );

    @Query("SELECT b FROM ClassBooking b " +
           "JOIN FETCH b.user member " +
           "JOIN FETCH b.session s " +
           "JOIN FETCH s.classEntity c " +
           "JOIN FETCH c.coach coach " +
           "JOIN FETCH c.subject subject " +
           "WHERE member.id = :memberId AND coach.id = :coachId " +
           "AND LOWER(b.status) = 'booked' " +
           "AND LOWER(c.status) IN ('active', 'open') " +
           "AND (c.endDate IS NULL OR c.endDate >= :today) " +
           "ORDER BY s.date DESC, b.id DESC")
    List<ClassBooking> findChatBookingsForPair(
            @Param("memberId") Integer memberId,
            @Param("coachId") Integer coachId,
            @Param("today") LocalDate today
    );
}
