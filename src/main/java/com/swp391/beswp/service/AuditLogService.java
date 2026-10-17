package com.swp391.beswp.service;

import com.swp391.beswp.dto.AuditLogDetailResponse;
import com.swp391.beswp.dto.AuditLogFilterOptionsResponse;
import com.swp391.beswp.dto.AuditLogItemResponse;
import com.swp391.beswp.dto.AuditLogListResponse;
import com.swp391.beswp.dto.AuditLogPerformerDto;
import com.swp391.beswp.entity.ActivityLog;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.repository.ActivityLogRepository;
import com.swp391.beswp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm - dd/MM/yyyy");
    private static final DateTimeFormatter DATE_ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_VN = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Lấy danh sách nhật ký kiểm toán (Audit Log) có phân trang, bộ lọc và tìm kiếm.
     */
    @Transactional(readOnly = true)
    public AuditLogListResponse getAuditLogs(
            String category,
            String action,
            String search,
            String startDateStr,
            String endDateStr,
            int page,
            int size
    ) {
        String targetEntity = resolveCategoryFilter(category);
        boolean isPackageTarget = "Package".equalsIgnoreCase(targetEntity);

        String actionGroup = resolveActionFilter(action);

        String keyword = normalizeText(search);
        Long parsedLogId = parseLogId(keyword);
        Integer parsedUserId = parseUserId(keyword);

        LocalDateTime fromDate = parseStartDate(startDateStr);
        LocalDateTime toDate = parseEndDate(endDateStr);

        int validPage = Math.max(0, page);
        int validSize = size > 0 ? size : 10;
        Pageable pageable = PageRequest.of(validPage, validSize, Sort.by(Sort.Direction.DESC, "createdAt", "id"));

        Page<ActivityLog> pageResult = activityLogRepository.searchAuditLogs(
                targetEntity,
                isPackageTarget,
                actionGroup,
                fromDate,
                toDate,
                keyword,
                parsedLogId,
                parsedUserId,
                pageable
        );

        List<AuditLogItemResponse> items = pageResult.getContent().stream()
                .map(this::mapToItemResponse)
                .toList();

        AuditLogFilterOptionsResponse filterOptions = buildFilterOptions();

        return AuditLogListResponse.builder()
                .success(true)
                .message("Lấy danh sách nhật ký kiểm toán thành công")
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .currentPage(pageResult.getNumber() + 1)
                .pageSize(pageResult.getSize())
                .numberOfElements(pageResult.getNumberOfElements())
                .filterOptions(filterOptions)
                .data(items)
                .build();
    }

    /**
     * Lấy chi tiết một bản ghi Audit Log.
     */
    @Transactional(readOnly = true)
    public AuditLogDetailResponse getAuditLogDetail(Long id) {
        ActivityLog log = activityLogRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhật ký kiểm toán với mã ID: " + id));

        return AuditLogDetailResponse.builder()
                .success(true)
                .message("Lấy thông tin chi tiết nhật ký thành công")
                .log(mapToItemResponse(log))
                .build();
    }

    /**
     * Tự động lấy User đang đăng nhập hiện tại từ SecurityContext.
     */
    public User getCurrentAuthenticatedUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equalsIgnoreCase(String.valueOf(auth.getPrincipal()))) {
                return null;
            }
            String principalStr = auth.getName();
            if (!StringUtils.hasText(principalStr)) {
                return null;
            }
            try {
                Integer userId = Integer.parseInt(principalStr);
                return userRepository.findById(userId).orElse(null);
            } catch (NumberFormatException e) {
                return userRepository.findByUsernameIgnoreCase(principalStr).orElse(null);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Ghi nhận một hành động vào ActivityLog.
     */
    @Transactional
    public void recordLog(User user, String actionType, String targetEntity, String description) {
        try {
            User actor = user != null ? user : getCurrentAuthenticatedUser();
            ActivityLog log = ActivityLog.builder()
                    .user(actor)
                    .actionType(actionType)
                    .targetEntity(targetEntity)
                    .description(description)
                    .createdAt(LocalDateTime.now())
                    .build();
            activityLogRepository.save(log);
        } catch (Exception ignored) {
            // Không ngắt mạch nghiệp vụ chính nếu ghi log thất bại
        }
    }

    @Transactional
    public void recordLog(String actionType, String targetEntity, String description) {
        recordLog(null, actionType, targetEntity, description);
    }

    private AuditLogItemResponse mapToItemResponse(ActivityLog log) {
        String logCode = String.format("#LOG-%04d", log.getId());
        AuditLogPerformerDto performer = mapPerformer(log.getUser());

        String displayCategory = mapDisplayCategory(log.getTargetEntity());
        String action = mapActionGroup(log.getActionType());
        String actionColor = mapActionColor(action);
        String formattedTime = log.getCreatedAt() != null ? log.getCreatedAt().format(TIME_FORMATTER) : "";

        return AuditLogItemResponse.builder()
                .id(log.getId())
                .logCode(logCode)
                .performer(performer)
                .category(displayCategory)
                .action(action)
                .actionColor(actionColor)
                .actionType(log.getActionType())
                .description(log.getDescription())
                .createdAt(log.getCreatedAt())
                .formattedTime(formattedTime)
                .build();
    }

    private AuditLogPerformerDto mapPerformer(User user) {
        if (user == null) {
            return AuditLogPerformerDto.builder()
                    .userId(null)
                    .userCode("SYS-000")
                    .fullName("Hệ thống")
                    .username("system")
                    .role("System")
                    .avatarInitials("HT")
                    .avatarUrl(null)
                    .displayText("SYS-000 (Hệ thống)")
                    .build();
        }

        String role = user.getRole() != null ? user.getRole().getRoleName() : "User";
        String prefix = resolveUserPrefix(role);
        String userCode = String.format("%s-%03d", prefix, user.getId());
        String fullName = StringUtils.hasText(user.getFullName()) ? user.getFullName() : user.getUsername();
        String initials = getInitials(fullName);
        String displayText = String.format("%s (%s)", userCode, fullName);

        return AuditLogPerformerDto.builder()
                .userId(user.getId())
                .userCode(userCode)
                .fullName(fullName)
                .username(user.getUsername())
                .role(role)
                .avatarInitials(initials)
                .avatarUrl(user.getAvatarUrl())
                .displayText(displayText)
                .build();
    }

    private String resolveUserPrefix(String role) {
        if (role == null) return "USR";
        String lower = role.toLowerCase(Locale.ROOT);
        if (lower.contains("admin") || lower.contains("manager")) return "ADM";
        if (lower.contains("receptionist") || lower.contains("lễ tân")) return "REC";
        if (lower.contains("coach") || lower.contains("huấn luyện")) return "COA";
        if (lower.contains("member") || lower.contains("hội viên")) return "MEM";
        return "USR";
    }

    private String getInitials(String fullName) {
        if (!StringUtils.hasText(fullName)) return "U";
        String[] words = fullName.trim().split("\\s+");
        if (words.length == 1) {
            return words[0].substring(0, Math.min(2, words[0].length())).toUpperCase(Locale.ROOT);
        }
        String first = words[0].substring(0, 1);
        String last = words[words.length - 1].substring(0, 1);
        return (first + last).toUpperCase(Locale.ROOT);
    }

    private String mapDisplayCategory(String targetEntity) {
        if (targetEntity == null) return "Khác";
        String lower = targetEntity.toLowerCase(Locale.ROOT);
        if (lower.contains("class_booking") || lower.contains("session") || lower.contains("class")) {
            return "Class";
        }
        if (lower.contains("subscription") || lower.contains("package")) {
            return "Package";
        }
        if (lower.contains("user") || lower.contains("member") || lower.contains("coach")) {
            return "User";
        }
        if (lower.contains("subject")) {
            return "Subject";
        }
        if (lower.contains("payment")) {
            return "Payment";
        }
        return targetEntity;
    }

    private String mapActionGroup(String actionType) {
        if (actionType == null) return "Other";
        String lower = actionType.toLowerCase(Locale.ROOT);
        if (lower.contains("create") || lower.contains("add") || lower.contains("insert") || lower.contains("book") || lower.contains("register") || lower.contains("purchase")) {
            return "Add";
        }
        if (lower.contains("update") || lower.contains("edit") || lower.contains("modify") || lower.contains("reset") || lower.contains("login") || lower.contains("change")) {
            return "Update";
        }
        if (lower.contains("delete") || lower.contains("remove") || lower.contains("cancel") || lower.contains("refund")) {
            return "Delete";
        }
        return actionType;
    }

    private String mapActionColor(String action) {
        if ("Add".equalsIgnoreCase(action)) return "success";  // green
        if ("Update".equalsIgnoreCase(action)) return "warning"; // orange
        if ("Delete".equalsIgnoreCase(action)) return "danger";  // red
        return "secondary";
    }

    private String resolveCategoryFilter(String category) {
        if (!StringUtils.hasText(category) || "tất cả".equalsIgnoreCase(category.trim()) || "all".equalsIgnoreCase(category.trim())) {
            return null;
        }
        String cat = category.trim().toLowerCase(Locale.ROOT);
        if (cat.contains("class")) return "Class";
        if (cat.contains("package")) return "Package";
        if (cat.contains("user")) return "User";
        if (cat.contains("subject")) return "Subject";
        if (cat.contains("payment") || cat.contains("thanh toán")) return "Payment";
        return category.trim();
    }

    private String resolveActionFilter(String action) {
        if (!StringUtils.hasText(action) || "tất cả".equalsIgnoreCase(action.trim()) || "all".equalsIgnoreCase(action.trim())) {
            return null;
        }
        String a = action.trim().toLowerCase(Locale.ROOT);
        if (a.contains("thêm") || a.contains("add") || a.contains("create")) return "ADD";
        if (a.contains("cập nhật") || a.contains("update") || a.contains("edit")) return "UPDATE";
        if (a.contains("xóa") || a.contains("delete") || a.contains("cancel")) return "DELETE";
        return null;
    }

    private LocalDateTime parseStartDate(String dateStr) {
        if (!StringUtils.hasText(dateStr)) return null;
        LocalDate d = parseLocalDate(dateStr.trim());
        return d != null ? d.atStartOfDay() : null;
    }

    private LocalDateTime parseEndDate(String dateStr) {
        if (!StringUtils.hasText(dateStr)) return null;
        LocalDate d = parseLocalDate(dateStr.trim());
        return d != null ? d.atTime(23, 59, 59) : null;
    }

    private LocalDate parseLocalDate(String raw) {
        try {
            return LocalDate.parse(raw, DATE_ISO);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(raw, DATE_VN);
            } catch (DateTimeParseException ignored2) {
                return null;
            }
        }
    }

    private String normalizeText(String text) {
        if (!StringUtils.hasText(text)) return null;
        String t = text.trim();
        return t.isEmpty() ? null : t;
    }

    private Long parseLogId(String keyword) {
        if (!StringUtils.hasText(keyword)) return null;
        String cleaned = keyword.trim().toUpperCase(Locale.ROOT);
        if (cleaned.startsWith("#LOG-")) {
            cleaned = cleaned.substring(5);
        } else if (cleaned.startsWith("LOG-")) {
            cleaned = cleaned.substring(4);
        } else if (cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1);
        }
        try {
            return Long.parseLong(cleaned);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer parseUserId(String keyword) {
        if (!StringUtils.hasText(keyword)) return null;
        String cleaned = keyword.trim().toUpperCase(Locale.ROOT);
        if (cleaned.startsWith("ADM-") || cleaned.startsWith("REC-") || cleaned.startsWith("COA-") || cleaned.startsWith("MEM-")) {
            try {
                return Integer.parseInt(cleaned.substring(4));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        try {
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private AuditLogFilterOptionsResponse buildFilterOptions() {
        Set<String> categories = new LinkedHashSet<>();
        categories.add("Tất cả");
        categories.add("Class");
        categories.add("Package");
        categories.add("Payment");
        categories.add("User");
        categories.add("Subject");

        List<String> distinct = activityLogRepository.findDistinctTargetEntities();
        for (String d : distinct) {
            categories.add(mapDisplayCategory(d));
        }

        List<AuditLogFilterOptionsResponse.ActionOption> actions = List.of(
                AuditLogFilterOptionsResponse.ActionOption.builder().key("ALL").label("Tất cả").color("default").build(),
                AuditLogFilterOptionsResponse.ActionOption.builder().key("ADD").label("+ Thêm mới").color("success").build(),
                AuditLogFilterOptionsResponse.ActionOption.builder().key("UPDATE").label("Cập nhật").color("warning").build(),
                AuditLogFilterOptionsResponse.ActionOption.builder().key("DELETE").label("Xóa dữ liệu").color("danger").build()
        );

        return AuditLogFilterOptionsResponse.builder()
                .categories(new ArrayList<>(categories))
                .actions(actions)
                .build();
    }
}
