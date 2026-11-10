package com.swp391.beswp.repository;

import com.swp391.beswp.entity.EmailOtpChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface EmailOtpChallengeRepository extends JpaRepository<EmailOtpChallenge, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from EmailOtpChallenge c where c.email = :email")
    Optional<EmailOtpChallenge> lockByEmail(@Param("email") String email);

    @Modifying
    @Query("delete from EmailOtpChallenge c where (c.lastSentAt is null or c.lastSentAt < :cutoff) " +
            "and (c.contextExpiresAt is null or c.contextExpiresAt < :now) " +
            "and (c.resetExpiresAt is null or c.resetExpiresAt < :now)")
    int deleteExpired(@Param("cutoff") Instant cutoff, @Param("now") Instant now);
}
