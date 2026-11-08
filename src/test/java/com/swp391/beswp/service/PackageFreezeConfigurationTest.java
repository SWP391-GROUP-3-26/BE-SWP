package com.swp391.beswp.service;

import com.swp391.beswp.dto.*;
import com.swp391.beswp.entity.MembershipPackage;
import com.swp391.beswp.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PackageFreezeConfigurationTest {
    final MembershipPackageRepository packages = mock(MembershipPackageRepository.class);
    final PackageService service = new PackageService(packages, mock(MemberPackageSubscriptionRepository.class),
            mock(SubjectRepository.class), mock(PackageIncludedSportRepository.class),
            mock(PackageChoiceGroupRepository.class), mock(PackageChoiceGroupSportRepository.class));

    CreatePackageRequest request(int duration, Integer freeze) {
        var request = new CreatePackageRequest();
        request.setName("Membership"); request.setDurationMonths(duration); request.setFreezeMonths(freeze);
        request.setPrice(new BigDecimal("1000000"));
        return request;
    }

    @Test void shortPackagesCannotEnableFreezing() {
        assertThrows(ResponseStatusException.class, () -> service.createPackage(request(3, 1)));
        verify(packages, never()).save(any());
    }

    @Test void sixMonthPackageExposesConfiguredBudget() {
        when(packages.save(any())).thenAnswer(call -> call.getArgument(0));
        var response = service.createPackage(request(6, 2));
        assertEquals(2, response.getData().getFreezeMonths());
        assertEquals(180, response.getData().getDurationDays());
    }

    @Test void omittedPolicyDefaultsToDisabled() {
        when(packages.save(any())).thenAnswer(call -> call.getArgument(0));
        assertEquals(0, service.createPackage(request(3, null)).getData().getFreezeMonths());
    }

    @Test void shorteningPackageRequiresDisablingFreezingInSameUpdate() {
        var pack = MembershipPackage.builder().id(1).duration(365).freezeMonths(2).build();
        when(packages.findById(1)).thenReturn(Optional.of(pack));
        var update = new UpdatePackageRequest(); update.setDurationMonths(3);
        assertThrows(ResponseStatusException.class, () -> service.updatePackage(1, update));
        assertEquals(365, pack.getDuration());
        update.setFreezeMonths(0);
        service.updatePackage(1, update);
        assertEquals(90, pack.getDuration()); assertEquals(0, pack.getFreezeMonths());
    }

    @Test void negativeBudgetIsRejectedEvenWhenServiceCalledWithoutBeanValidation() {
        assertThrows(ResponseStatusException.class, () -> service.createPackage(request(12, -1)));
        verify(packages, never()).save(any());
    }
}
