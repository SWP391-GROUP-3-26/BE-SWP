package com.swp391.beswp.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class VoucherPaymentLinkRepository {

    private final EntityManager entityManager;

    /** The existing Payment table already has Voucher_ID; Payment's legacy entity does not map it. */
    public int attachVoucher(Integer paymentId, Integer voucherId) {
        return entityManager.createNativeQuery("""
                        UPDATE dbo.Payment
                           SET Voucher_ID = :voucherId
                         WHERE Payment_ID = :paymentId
                           AND Voucher_ID IS NULL
                        """)
                .setParameter("voucherId", voucherId)
                .setParameter("paymentId", paymentId)
                .executeUpdate();
    }

    public boolean hasVoucher(Integer paymentId) {
        Number count = (Number) entityManager.createNativeQuery("""
                        SELECT COUNT(1)
                          FROM dbo.Payment
                         WHERE Payment_ID = :paymentId
                           AND Voucher_ID IS NOT NULL
                        """)
                .setParameter("paymentId", paymentId)
                .getSingleResult();
        return count.longValue() > 0;
    }
}
