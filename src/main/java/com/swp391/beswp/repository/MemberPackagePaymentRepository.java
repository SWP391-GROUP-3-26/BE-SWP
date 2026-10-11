package com.swp391.beswp.repository;

import com.swp391.beswp.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MemberPackagePaymentRepository extends JpaRepository<Payment, Integer> {

    Optional<Payment> findFirstBySubscription_IdOrderByIdDesc(Integer subscriptionId);

    List<Payment> findBySubscription_IdInOrderByIdDesc(Collection<Integer> subscriptionIds);
}
