package com.swp391.beswp.service;

import com.swp391.beswp.dto.CreatePackageRequest;
import com.swp391.beswp.dto.PackageListResponse;
import com.swp391.beswp.dto.PackageResponse;
import com.swp391.beswp.dto.UpdatePackageRequest;
import com.swp391.beswp.entity.MembershipPackage;
import com.swp391.beswp.entity.PackageChoiceGroup;
import com.swp391.beswp.entity.PackageChoiceGroupSport;
import com.swp391.beswp.entity.PackageIncludedSport;
import com.swp391.beswp.entity.Subject;
import com.swp391.beswp.repository.MembershipPackageRepository;
import com.swp391.beswp.repository.MemberPackageSubscriptionRepository;
import com.swp391.beswp.repository.PackageChoiceGroupRepository;
import com.swp391.beswp.repository.PackageChoiceGroupSportRepository;
import com.swp391.beswp.repository.PackageIncludedSportRepository;
import com.swp391.beswp.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PackageService {
    private final MembershipPackageRepository packages;
    private final MemberPackageSubscriptionRepository subscriptionRepository;
    private final SubjectRepository subjects;
    private final PackageIncludedSportRepository includedSports;
    private final PackageChoiceGroupRepository choiceGroups;
    private final PackageChoiceGroupSportRepository choiceGroupSports;

    @Transactional
    public PackageResponse createPackage(CreatePackageRequest request) {
        String name = required(request.getName(), "Tên gói dịch vụ không được để trống");
        if (packages.existsByNameIgnoreCase(name)) throw conflict("Tên gói dịch vụ đã tồn tại");
        validateStatus(request.getStatus());
        int freezeMonths = request.getFreezeMonths() == null ? 0 : request.getFreezeMonths();
        validateFreezePolicy(toDays(request.getDurationMonths()), freezeMonths);
        MembershipPackage pkg = packages.save(MembershipPackage.builder()
                .name(name).description(request.getDescription()).price(request.getPrice())
                .duration(toDays(request.getDurationMonths())).discount(discount(request.getDiscount())).freezeMonths(freezeMonths)
                .termConditions(request.getTermConditions()).status(status(request.getStatus())).build());
        replaceBenefits(pkg, request.getIncludedSportIds(), request.getChoiceGroups());
        return PackageResponse.success("Tạo mới gói dịch vụ thành công", packageData(pkg));
    }

    @Transactional(readOnly = true)
    public PackageListResponse getAllPackages(String search, String status) {
        String filter = normalize(status);
        if ("all".equalsIgnoreCase(filter) || "tất cả".equalsIgnoreCase(filter)) filter = null;
        return PackageListResponse.success(packages.searchPackages(normalize(search), filter));
    }

    @Transactional(readOnly = true)
    public PackageResponse getPackageById(Integer id) {
        MembershipPackage pkg = find(id);
        return PackageResponse.success("Lấy thông tin gói dịch vụ thành công", packageData(pkg));
    }

    @Transactional
    public PackageResponse updatePackage(Integer id, UpdatePackageRequest request) {
        MembershipPackage pkg = find(id);
        validateFreezePolicy(request.getDurationMonths() == null ? pkg.getDuration() : toDays(request.getDurationMonths()),
                request.getFreezeMonths() == null ? pkg.getFreezeMonths() : request.getFreezeMonths());
        if (StringUtils.hasText(request.getName())) {
            String name = required(request.getName(), "Tên gói dịch vụ không được để trống");
            if (!name.equalsIgnoreCase(pkg.getName()) && packages.existsByNameIgnoreCase(name)) throw conflict("Tên gói dịch vụ đã tồn tại");
            pkg.setName(name);
        }
        if (request.getDescription() != null) pkg.setDescription(request.getDescription());
        if (request.getPrice() != null) pkg.setPrice(request.getPrice());
        if (request.getDurationMonths() != null) pkg.setDuration(toDays(request.getDurationMonths()));
        if (request.getFreezeMonths() != null) pkg.setFreezeMonths(request.getFreezeMonths());
        if (request.getDiscount() != null) pkg.setDiscount(discount(request.getDiscount()));
        if (request.getTermConditions() != null) pkg.setTermConditions(request.getTermConditions());
        if (StringUtils.hasText(request.getStatus())) { validateStatus(request.getStatus()); pkg.setStatus(request.getStatus().trim()); }
        packages.save(pkg);
        if (request.getIncludedSportIds() != null || request.getChoiceGroups() != null) {
            List<Integer> included = request.getIncludedSportIds() != null ? request.getIncludedSportIds()
                    : includedSports.findByPackageId(id).stream().map(x -> x.getSubject().getId()).toList();
            replaceBenefits(pkg, included, request.getChoiceGroups());
        }
        return PackageResponse.success("Cập nhật gói dịch vụ thành công", packageData(pkg));
    }

    @Transactional
    public void deletePackage(Integer id) {
        MembershipPackage pkg = find(id);
        if (subscriptionRepository.existsByMembershipPackage_Id(id)) {
            throw conflict("Gói đã có lịch sử mua, chỉ có thể chuyển sang Inactive để bảo toàn dữ liệu lịch sử");
        }
        choiceGroupSports.deleteByPackageId(id);
        choiceGroups.deleteByPackageId(id);
        includedSports.deleteByPackageId(id);
        packages.delete(pkg);
    }

    private void replaceBenefits(MembershipPackage pkg, List<Integer> includedIds,
                                 List<CreatePackageRequest.ChoiceGroupRequest> groups) {
        if (includedIds != null) {
            uniqueIds(includedIds, "Included Sport");
            includedSports.deleteByPackageId(pkg.getId());
            for (Integer subjectId : includedIds) includedSports.save(PackageIncludedSport.builder()
                    .membershipPackage(pkg).subject(subject(subjectId)).build());
        }
        if (groups != null) {
            choiceGroupSports.deleteByPackageId(pkg.getId());
            choiceGroups.deleteByPackageId(pkg.getId());
            for (int index = 0; index < groups.size(); index++) {
                CreatePackageRequest.ChoiceGroupRequest input = groups.get(index);
                List<Integer> sportIds = input.getSportIds();
                if (sportIds == null || input.getRequiredSelection() == null || sportIds.size() < input.getRequiredSelection())
                    throw badRequest("Choice Group phải có ít nhất requiredSelection Sport");
                uniqueIds(sportIds, "Sport trong Choice Group");
                PackageChoiceGroup group = choiceGroups.save(PackageChoiceGroup.builder().membershipPackage(pkg)
                        .groupName(required(input.getGroupName(), "Tên Choice Group không được để trống"))
                        .description(input.getDescription()).requiredSelection(input.getRequiredSelection())
                        .sortOrder(input.getSortOrder() == null ? index : input.getSortOrder()).build());
                for (Integer subjectId : sportIds) choiceGroupSports.save(PackageChoiceGroupSport.builder()
                        .choiceGroup(group).subject(subject(subjectId)).build());
            }
        }
    }

    private PackageResponse.PackageData packageData(MembershipPackage pkg) {
        PackageResponse.PackageData base = PackageResponse.PackageData.fromEntity(pkg);
        List<PackageResponse.SportData> included = includedSports.findByPackageId(pkg.getId()).stream()
                .map(item -> PackageResponse.SportData.builder()
                        .id(item.getSubject().getId())
                        .name(item.getSubject().getName())
                        .build())
                .toList();

        List<PackageChoiceGroup> persistedGroups = choiceGroups.findByPackageId(pkg.getId());
        List<Integer> groupIds = persistedGroups.stream().map(PackageChoiceGroup::getId).toList();
        Map<Integer, List<PackageChoiceGroupSport>> sportsByGroup = groupIds.isEmpty()
                ? Collections.emptyMap()
                : choiceGroupSports.findByChoiceGroupIdIn(groupIds).stream()
                        .collect(Collectors.groupingBy(item -> item.getChoiceGroup().getId()));
        List<PackageResponse.ChoiceGroupData> groups = persistedGroups.stream()
                .map(group -> PackageResponse.ChoiceGroupData.builder()
                        .id(group.getId())
                        .name(group.getGroupName())
                        .description(group.getDescription())
                        .requiredSelection(group.getRequiredSelection())
                        .sortOrder(group.getSortOrder())
                        .sports(sportsByGroup.getOrDefault(group.getId(), List.of()).stream()
                                .map(item -> PackageResponse.SportData.builder()
                                        .id(item.getSubject().getId())
                                        .name(item.getSubject().getName())
                                        .build())
                                .toList())
                        .build())
                .toList();

        return PackageResponse.PackageData.builder()
                .code(base.getCode())
                .id(base.getId())
                .name(base.getName())
                .description(base.getDescription())
                .price(base.getPrice())
                .discount(base.getDiscount())
                .durationDays(base.getDurationDays())
                .durationMonths(base.getDurationMonths())
                .freezeMonths(base.getFreezeMonths())
                .freezeTerms(base.getFreezeTerms())
                .benefits(base.getBenefits())
                .termConditions(base.getTermConditions())
                .status(base.getStatus())
                .statusLabel(base.getStatusLabel())
                .includedSports(included)
                .choiceGroups(groups)
                .build();
    }

    private Subject subject(Integer id) {
        if (id == null || id <= 0) throw badRequest("Sport không hợp lệ");
        Subject subject = subjects.findById(id).orElseThrow(() -> badRequest("Sport không tồn tại: " + id));
        if (!"Active".equalsIgnoreCase(subject.getStatus())) {
            throw badRequest("Sport đã ngừng cung cấp, không thể đưa vào Package: " + id);
        }
        return subject;
    }
    private void uniqueIds(List<Integer> ids, String label) {
        if (ids.stream().anyMatch(id -> id == null || id <= 0) || new HashSet<>(ids).size() != ids.size())
            throw badRequest(label + " không được trùng hoặc không hợp lệ");
    }
    private void validateFreezePolicy(Integer durationDays, Integer freezeMonths) {
        if (freezeMonths == null || freezeMonths < 0) throw badRequest("Thời gian bảo lưu không được âm");
        if (freezeMonths > 0 && (durationDays == null || durationDays < MembershipFreezePolicy.MINIMUM_DURATION_DAYS)) {
            throw badRequest("Chỉ gói có thời hạn từ 6 tháng mới được cấu hình bảo lưu");
        }
    }

    private int toDays(Integer months) {
        if (months == null || months <= 0) throw badRequest("Thời gian hiệu lực phải lớn hơn 0 tháng");
        return switch (months) { case 12 -> 365; case 24 -> 730; default -> months * 30; };
    }
    private BigDecimal discount(BigDecimal value) {
        BigDecimal result = value == null ? BigDecimal.ZERO : value;
        if (result.signum() < 0 || result.compareTo(new BigDecimal("100")) > 0) throw badRequest("Chiết khấu phải từ 0 đến 100");
        return result;
    }
    private void validateStatus(String value) {
        if (StringUtils.hasText(value) && !"Active".equalsIgnoreCase(value.trim()) && !"Inactive".equalsIgnoreCase(value.trim()))
            throw badRequest("Trạng thái chỉ được là Active hoặc Inactive");
    }
    private String status(String value) { return StringUtils.hasText(value) ? value.trim() : "Active"; }
    private MembershipPackage find(Integer id) {
        if (id == null || id <= 0) throw badRequest("packageId không hợp lệ");
        return packages.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy gói dịch vụ"));
    }
    private String normalize(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private String required(String value, String message) { String normalized = normalize(value); if (normalized == null) throw badRequest(message); return normalized; }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
