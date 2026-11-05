package com.swp391.beswp.repository;

import com.swp391.beswp.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Integer> {

    Optional<Voucher> findByCodeIgnoreCase(String code);

    @Query("SELECT v FROM Voucher v WHERE v.startDate <= :today AND v.endDate >= :today ORDER BY v.id")
    List<Voucher> findAvailableOn(@Param("today") LocalDate today);
}
