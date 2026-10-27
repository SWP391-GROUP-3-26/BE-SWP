package com.swp391.beswp.service;

import com.swp391.beswp.dto.MemberPackageRegistrationRequest;
import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberPackageOnlinePaymentServiceTest {
    @Mock MembershipPackageRepository packageRepository;
    @Mock MemberPackageSubscriptionRepository subscriptionRepository;
    @Mock MemberPackagePaymentRepository paymentRepository;
    @Mock UserRepository userRepository;
    @Mock AuditLogService auditLogService;
    @Mock OnlineSubscriptionPaymentService subscriptionPayments;
    @Mock PackageChoiceGroupRepository choiceGroupRepository;
    @Mock PackageChoiceGroupSportRepository choiceGroupSportRepository;
    @Mock MembershipSportSelectionRepository selectionRepository;
    @Mock MembershipIncludedSportRepository includedSnapshotRepository;
    @Mock PackageIncludedSportRepository includedSportRepository;
    @Mock SubjectRepository subjectRepository;
    @Mock VnpayService vnpay;
    @Mock SepayService sepay;
    @Spy Clock paymentClock = Clock.fixed(Instant.parse("2026-10-26T03:00:00Z"), VnpayService.OFFSET);
    @InjectMocks MemberPackageService service;
    User member;
    MembershipPackage pack;
    Payment savedPayment;

    @BeforeEach void setup() {
        Role role = new Role(); role.setRoleName("Member");
        member = new User(); member.setId(7); member.setRole(role); member.setStatus("Active"); member.setFullName("Test Member");
        pack = MembershipPackage.builder().id(2).name("Standard").status("Active").duration(30)
                .price(new BigDecimal("100000")).discount(new BigDecimal("10")).build();
        lenient().when(userRepository.findByIdAndRoleRoleNameIgnoreCase(7, "Member")).thenReturn(Optional.of(member));
        lenient().when(userRepository.findByIdForUpdate(7)).thenReturn(Optional.of(member));
        lenient().when(packageRepository.findById(2)).thenReturn(Optional.of(pack));
        lenient().when(subscriptionRepository.save(any())).thenAnswer(call -> {
            MemberSubscription subscription = call.getArgument(0); subscription.setId(10); return subscription;
        });
        lenient().when(paymentRepository.save(any())).thenAnswer(call -> {
            savedPayment = call.getArgument(0); savedPayment.setId(20); return savedPayment;
        });
    }

    MemberPackageRegistrationRequest request(String method, String start) {
        var request = new MemberPackageRegistrationRequest();
        request.setPackageId(2); request.setPaymentMethod(method); request.setRenewalStart(start);
        return request;
    }

    @Test void cardPurchaseCreatesPendingDiscountedOrderAndGatewayUrl() {
        when(vnpay.createPaymentUrl(any(), eq("127.0.0.1"))).thenReturn("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?test");
        var result = service.registerOnlinePackage("7", request("Card", "IMMEDIATE"), "127.0.0.1", false);
        assertEquals("Pending", result.getData().getSubscriptionStatus());
        assertEquals("Pending", result.getData().getPaymentStatus());
        assertTrue(result.getData().getPaymentUrl().contains("vnpayment.vn"));
        assertEquals(0, new BigDecimal("90000").compareTo(savedPayment.getAmount()));
        assertEquals(LocalDateTime.now(paymentClock).plusMinutes(15), savedPayment.getExpiresAt());
        assertNull(savedPayment.getRenewalSubscription());
    }

    @Test void immediateQrRenewalDoesNotExpireOldPackageBeforePayment() {
        var current = MemberSubscription.builder().id(4).user(member).membershipPackage(pack).status("Active")
                .startDate(LocalDate.now(paymentClock).minusDays(2)).endDate(LocalDate.now(paymentClock).plusDays(28)).build();
        when(subscriptionRepository.findCurrentActiveSubscriptions(7, LocalDate.now(paymentClock))).thenReturn(List.of(current));
        var result = service.registerOnlinePackage("7", request("E_Wallet", "IMMEDIATE"), "127.0.0.1", true);
        assertEquals("Active", current.getStatus());
        assertEquals("Pending", result.getData().getSubscriptionStatus());
        assertSame(current, savedPayment.getRenewalSubscription()); assertEquals("IMMEDIATE", savedPayment.getRenewalStart());
        verify(sepay).requireConfigured(); verify(vnpay, never()).createPaymentUrl(any(), any());
    }

    @Test void cashAndBankTransferAreRejectedBeforeCreatingMembership() {
        for (String method : List.of("Cash", "Bank_Transfer")) {
            assertEquals(400, assertThrows(ResponseStatusException.class, () ->
                    service.registerOnlinePackage("7", request(method, "IMMEDIATE"), "127.0.0.1", false)).getStatusCode().value());
        }
        verify(subscriptionRepository, never()).save(any()); verify(paymentRepository, never()).save(any());
    }

    @Test void renewalRequiresCurrentMembershipAndPendingOrderBlocksAnotherPurchase() {
        assertEquals(409, assertThrows(ResponseStatusException.class, () ->
                service.registerOnlinePackage("7", request("Card", "IMMEDIATE"), "127.0.0.1", true)).getStatusCode().value());
        when(subscriptionPayments.hasPending(7)).thenReturn(true);
        assertEquals(409, assertThrows(ResponseStatusException.class, () ->
                service.registerOnlinePackage("7", request("Card", "IMMEDIATE"), "127.0.0.1", false)).getStatusCode().value());
        verify(subscriptionRepository, never()).save(any());
    }
}
