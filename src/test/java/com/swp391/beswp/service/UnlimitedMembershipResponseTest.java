package com.swp391.beswp.service;

import com.swp391.beswp.dto.*;
import com.swp391.beswp.entity.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UnlimitedMembershipResponseTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test void quotaFieldsAreAbsentAcrossMembershipResponses() {
        var pack = MembershipPackage.builder().id(1).name("Membership by term").duration(90).build();
        var sub = MemberSubscription.builder().id(2).membershipPackage(pack).startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2027, 1, 29)).build();
        var responses = List.of(PackageResponse.PackageData.fromEntity(pack),
                MemberPackageSubscriptionData.fromEntities(sub, null), MemberResponse.PackageData.fromEntity(sub),
                MemberSubscriptionInfoResponse.builder().hasActivePackage(true).build(),
                MemberClassBookingResponse.builder().sessionCount(8).build(),
                MyClassItemResponse.builder().sessionCount(8).build(),
                ClassConfirmationDetailResponse.PackagePaymentOption.builder().eligible(true).build());
        for (Object response : responses) {
            var body = json.valueToTree(response);
            for (String field : List.of("includedClasses", "remainingClasses", "remainingSessions", "totalSessions",
                    "usedSessions", "heldSessions", "remainingPackageSessions", "heldPackageSessions", "mixedEligible")) {
                assertFalse(body.has(field), response.getClass().getSimpleName() + ": " + field);
            }
        }
        assertEquals(8, json.valueToTree(responses.get(4)).get("sessionCount").asInt());
        assertTrue(json.valueToTree(responses.get(6)).get("eligible").asBoolean());
    }
}
