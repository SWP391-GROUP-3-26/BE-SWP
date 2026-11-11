package com.swp391.beswp.service;

import com.swp391.beswp.dto.PackageResponse;
import com.swp391.beswp.entity.MembershipPackage;
import com.swp391.beswp.entity.PackageChoiceGroup;
import com.swp391.beswp.entity.PackageChoiceGroupSport;
import com.swp391.beswp.entity.PackageIncludedSport;
import com.swp391.beswp.entity.Subject;
import com.swp391.beswp.repository.MemberPackageSubscriptionRepository;
import com.swp391.beswp.repository.MembershipPackageRepository;
import com.swp391.beswp.repository.PackageChoiceGroupRepository;
import com.swp391.beswp.repository.PackageChoiceGroupSportRepository;
import com.swp391.beswp.repository.PackageIncludedSportRepository;
import com.swp391.beswp.repository.SubjectRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PackageServiceTest {

    private final MembershipPackageRepository packages = mock(MembershipPackageRepository.class);
    private final MemberPackageSubscriptionRepository subscriptions = mock(MemberPackageSubscriptionRepository.class);
    private final SubjectRepository subjects = mock(SubjectRepository.class);
    private final PackageIncludedSportRepository includedSports = mock(PackageIncludedSportRepository.class);
    private final PackageChoiceGroupRepository choiceGroups = mock(PackageChoiceGroupRepository.class);
    private final PackageChoiceGroupSportRepository choiceGroupSports = mock(PackageChoiceGroupSportRepository.class);
    private final PackageService service = new PackageService(
            packages, subscriptions, subjects, includedSports, choiceGroups, choiceGroupSports);

    @Test
    void getPackageByIdReloadsPersistedIncludedAndOptionalSports() {
        MembershipPackage pkg = MembershipPackage.builder()
                .id(10)
                .name("Gói Kim Cương")
                .price(new BigDecimal("4200000"))
                .discount(BigDecimal.ZERO)
                .duration(180)
                .freezeMonths(1)
                .status("Active")
                .build();
        Subject yoga = subject(1, "Yoga");
        Subject swimming = subject(2, "Bơi lội");
        Subject tennis = subject(3, "Tennis");
        PackageIncludedSport included = PackageIncludedSport.builder()
                .id(100).membershipPackage(pkg).subject(yoga).build();
        PackageChoiceGroup group = PackageChoiceGroup.builder()
                .id(200).membershipPackage(pkg).groupName("Môn tự chọn")
                .requiredSelection(1).sortOrder(0).build();
        PackageChoiceGroupSport swimmingOption = PackageChoiceGroupSport.builder()
                .id(300).choiceGroup(group).subject(swimming).build();
        PackageChoiceGroupSport tennisOption = PackageChoiceGroupSport.builder()
                .id(301).choiceGroup(group).subject(tennis).build();

        when(packages.findById(10)).thenReturn(Optional.of(pkg));
        when(includedSports.findByPackageId(10)).thenReturn(List.of(included));
        when(choiceGroups.findByPackageId(10)).thenReturn(List.of(group));
        when(choiceGroupSports.findByChoiceGroupIdIn(List.of(200)))
                .thenReturn(List.of(swimmingOption, tennisOption));

        PackageResponse.PackageData data = service.getPackageById(10).getData();

        assertNotNull(data.getIncludedSports());
        assertEquals(List.of("Yoga"), data.getIncludedSports().stream()
                .map(PackageResponse.SportData::getName).toList());
        assertNotNull(data.getChoiceGroups());
        assertEquals(1, data.getChoiceGroups().size());
        assertEquals("Môn tự chọn", data.getChoiceGroups().getFirst().getName());
        assertEquals(List.of("Bơi lội", "Tennis"), data.getChoiceGroups().getFirst().getSports().stream()
                .map(PackageResponse.SportData::getName).toList());
        verify(includedSports).findByPackageId(10);
        verify(choiceGroups).findByPackageId(10);
        verify(choiceGroupSports).findByChoiceGroupIdIn(List.of(200));
    }

    @Test
    void getPackageByIdReturnsEmptyListsWhenNoSportsWereConfigured() {
        MembershipPackage pkg = MembershipPackage.builder()
                .id(11)
                .name("Gói Cơ Bản")
                .price(new BigDecimal("850000"))
                .discount(BigDecimal.ZERO)
                .duration(30)
                .freezeMonths(0)
                .status("Active")
                .build();
        when(packages.findById(11)).thenReturn(Optional.of(pkg));
        when(includedSports.findByPackageId(11)).thenReturn(List.of());
        when(choiceGroups.findByPackageId(11)).thenReturn(List.of());

        PackageResponse.PackageData data = service.getPackageById(11).getData();

        assertNotNull(data.getIncludedSports());
        assertNotNull(data.getChoiceGroups());
        assertEquals(0, data.getIncludedSports().size());
        assertEquals(0, data.getChoiceGroups().size());
    }

    private Subject subject(int id, String name) {
        return Subject.builder().id(id).name(name).status("Active").build();
    }
}
