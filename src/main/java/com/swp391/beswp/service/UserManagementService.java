package com.swp391.beswp.service;

import com.swp391.beswp.dto.CreateManagedUserRequest;
import com.swp391.beswp.dto.ResetManagedUserPasswordRequest;
import com.swp391.beswp.dto.UpdateManagedUserRequest;
import com.swp391.beswp.dto.UserManagementListResponse;
import com.swp391.beswp.dto.UserManagementOptionsResponse;
import com.swp391.beswp.dto.UserManagementResponse;
import com.swp391.beswp.dto.UserManagementUserData;
import com.swp391.beswp.entity.Role;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.repository.RoleRepository;
import com.swp391.beswp.repository.UserManagementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserManagementService {

    public static final List<String> MANAGED_ROLES = List.of("Receptionist", "Coach", "Member");
    public static final List<String> STATUSES = List.of("Active", "Inactive", "Locked");
    private static final List<String> GENDERS = List.of("Male", "Female", "Other");
    private static final Set<String> SUPPORTED_PHONE_PREFIXES = Set.of(
            "032", "033", "034", "035", "036", "037", "038", "039", "086", "096", "097", "098",
            "070", "076", "077", "078", "079", "089", "090", "093",
            "081", "082", "083", "084", "085", "088", "091", "094"
    );
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9._]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^0\\d{9}$");
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$");

    private final UserManagementRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserManagementListResponse searchUsers(String search, String role, String status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw badRequest("page must be >= 0; size must be between 1 and 100");
        }

        Specification<User> filter = buildFilter(search, role, status);
        Page<User> users = userRepository.findAll(
                filter,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"))
        );
        return UserManagementListResponse.fromPage(users);
    }

    public UserManagementResponse getUser(Integer id) {
        User user = findManagedUser(id);
        return UserManagementResponse.success("Lấy thông tin người dùng thành công", UserManagementUserData.fromEntity(user));
    }

    public UserManagementOptionsResponse getOptions() {
        List<String> roles = roleRepository.findAll().stream()
                .map(Role::getRoleName)
                .filter(roleName -> MANAGED_ROLES.stream().anyMatch(roleName::equalsIgnoreCase))
                .sorted((left, right) -> Integer.compare(roleOrder(left), roleOrder(right)))
                .toList();
        return new UserManagementOptionsResponse(true, "Lấy danh mục thành công", roles, STATUSES, GENDERS);
    }

    @Transactional
    public UserManagementResponse createUser(CreateManagedUserRequest request) {
        String username = normalizeUsername(request.getUsername());
        String fullName = normalizeRequiredText(request.getFullName(), 100, "Full name");
        String email = normalizeEmail(request.getEmail());
        String phone = normalizePhone(request.getPhone());
        String roleName = normalizeChoice(request.getRole(), MANAGED_ROLES, "Role");
        String gender = normalizeGender(request.getGender());
        String password = validatePassword(request.getPassword(), request.getConfirmPassword());

        assertUsernameAvailable(username, null);
        assertEmailAvailable(email, null);
        assertPhoneAvailable(phone, null);

        Role role = roleRepository.findByRoleNameIgnoreCase(roleName)
                .orElseThrow(() -> conflict("Role is not configured: " + roleName));

        User user = new User();
        user.setUsername(username);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setDob(request.getDob());
        user.setGender(gender);
        user.setAddress(normalizeOptionalText(request.getAddress()));
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user.setStatus("Active");

        User saved = userRepository.saveAndFlush(user);
        return UserManagementResponse.success("Tạo tài khoản thành công", UserManagementUserData.fromEntity(saved));
    }

    @Transactional
    public UserManagementResponse updateUser(Integer id, UpdateManagedUserRequest request) {
        User user = findManagedUser(id);
        String username = normalizeUsername(request.getUsername());
        String fullName = normalizeRequiredText(request.getFullName(), 100, "Full name");
        String email = normalizeEmail(request.getEmail());
        String phone = normalizePhone(request.getPhone());

        assertUsernameAvailable(username, id);
        assertEmailAvailable(email, id);
        assertPhoneAvailable(phone, id);

        user.setUsername(username);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setDob(request.getDob());
        user.setGender(normalizeGender(request.getGender()));
        user.setAddress(normalizeOptionalText(request.getAddress()));

        User saved = userRepository.saveAndFlush(user);
        return UserManagementResponse.success("Cập nhật người dùng thành công", UserManagementUserData.fromEntity(saved));
    }

    @Transactional
    public UserManagementResponse updateRole(Integer id, String requestedRole) {
        User user = findManagedUser(id);
        String roleName = normalizeChoice(requestedRole, MANAGED_ROLES, "Role");
        Role role = roleRepository.findByRoleNameIgnoreCase(roleName)
                .orElseThrow(() -> conflict("Role is not configured: " + roleName));
        user.setRole(role);
        User saved = userRepository.saveAndFlush(user);
        return UserManagementResponse.success("Cập nhật vai trò thành công", UserManagementUserData.fromEntity(saved));
    }

    @Transactional
    public UserManagementResponse updateStatus(Integer id, String requestedStatus) {
        User user = findManagedUser(id);
        String status = normalizeChoice(requestedStatus, STATUSES, "Status");
        user.setStatus(status);
        User saved = userRepository.saveAndFlush(user);
        return UserManagementResponse.success("Cập nhật trạng thái thành công", UserManagementUserData.fromEntity(saved));
    }

    @Transactional
    public UserManagementResponse resetPassword(Integer id, ResetManagedUserPasswordRequest request) {
        User user = findManagedUser(id);
        String password = validatePassword(request.getPassword(), request.getConfirmPassword());
        user.setPassword(passwordEncoder.encode(password));
        User saved = userRepository.saveAndFlush(user);
        return UserManagementResponse.success("Đặt lại mật khẩu thành công", UserManagementUserData.fromEntity(saved));
    }

    public byte[] exportCsv(String search, String role, String status) {
        List<User> users = userRepository.findAll(buildFilter(search, role, status), Sort.by(Sort.Direction.ASC, "id"));
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("UID,Họ tên,Tên tài khoản,Email,Số điện thoại,Vai trò,Trạng thái\r\n");
        for (User user : users) {
            csv.append(csvCell(user.getId() == null ? null : String.valueOf(user.getId()))).append(',')
                    .append(csvCell(user.getFullName())).append(',')
                    .append(csvCell(user.getUsername())).append(',')
                    .append(csvCell(user.getEmail())).append(',')
                    .append(csvCell(user.getPhone())).append(',')
                    .append(csvCell(user.getRole() == null ? null : user.getRole().getRoleName())).append(',')
                    .append(csvCell(user.getStatus())).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Specification<User> buildFilter(String search, String requestedRole, String requestedStatus) {
        String keyword = normalizeSearch(search);
        String role = normalizeOptionalChoice(requestedRole, MANAGED_ROLES, "Role");
        String status = normalizeOptionalChoice(requestedStatus, STATUSES, "Status");
        Integer userId = parseUserId(keyword);
        String escapedKeyword = keyword == null ? null : escapeLike(keyword.toLowerCase(Locale.ROOT));

        return (root, query, criteriaBuilder) -> {
            Join<User, Role> roleJoin = root.join("role", JoinType.INNER);
            List<Predicate> predicates = new ArrayList<>();
            var roleName = criteriaBuilder.lower(roleJoin.<String>get("roleName"));
            predicates.add(roleName.in(MANAGED_ROLES.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList()));

            if (role != null) {
                predicates.add(criteriaBuilder.equal(roleName, role.toLowerCase(Locale.ROOT)));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.<String>get("status")),
                        status.toLowerCase(Locale.ROOT)
                ));
            }
            if (escapedKeyword != null) {
                String pattern = "%" + escapedKeyword + "%";
                List<Predicate> searchPredicates = new ArrayList<>();
                searchPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.<String>get("fullName")), pattern, '\\'));
                searchPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.<String>get("username")), pattern, '\\'));
                searchPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.<String>get("email")), pattern, '\\'));
                searchPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.<String>get("phone")), pattern, '\\'));
                if (userId != null) {
                    searchPredicates.add(criteriaBuilder.equal(root.get("id"), userId));
                }
                predicates.add(criteriaBuilder.or(searchPredicates.toArray(Predicate[]::new)));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private User findManagedUser(Integer id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> notFound("Không tìm thấy người dùng với mã ID: " + id));
        if (user.getRole() == null || MANAGED_ROLES.stream()
                .noneMatch(role -> role.equalsIgnoreCase(user.getRole().getRoleName()))) {
            throw notFound("Không tìm thấy người dùng với mã ID: " + id);
        }
        return user;
    }

    private void assertUsernameAvailable(String username, Integer exceptId) {
        boolean exists = exceptId == null
                ? userRepository.existsByUsernameIgnoreCase(username)
                : userRepository.existsByUsernameIgnoreCaseAndIdNot(username, exceptId);
        if (exists) throw conflict("Username đã tồn tại");
    }

    private void assertEmailAvailable(String email, Integer exceptId) {
        boolean exists = exceptId == null
                ? userRepository.existsByEmailIgnoreCase(email)
                : userRepository.existsByEmailIgnoreCaseAndIdNot(email, exceptId);
        if (exists) throw conflict("Email đã tồn tại");
    }

    private void assertPhoneAvailable(String phone, Integer exceptId) {
        if (phone == null) return;
        boolean exists = exceptId == null
                ? userRepository.existsByPhone(phone)
                : userRepository.existsByPhoneAndIdNot(phone, exceptId);
        if (exists) throw conflict("Phone đã tồn tại");
    }

    private String normalizeUsername(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.length() > 50 || !USERNAME_PATTERN.matcher(value).matches()) {
            throw badRequest("Username không hợp lệ");
        }
        return value;
    }

    private String normalizeRequiredText(String rawValue, int maxLength, String label) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (!StringUtils.hasText(value) || value.length() > maxLength) {
            throw badRequest(label + " không hợp lệ");
        }
        return value;
    }

    private String normalizeEmail(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
        if (value.length() > 100 || !EMAIL_PATTERN.matcher(value).matches()) {
            throw badRequest("Email không hợp lệ");
        }
        return value;
    }

    private String normalizePhone(String rawValue) {
        if (!StringUtils.hasText(rawValue)) return null;
        String phone = rawValue.trim().replaceAll("[ .()\\-]", "");
        if (phone.startsWith("+84")) {
            phone = "0" + phone.substring(3);
        } else if (phone.startsWith("84") && phone.length() == 11) {
            phone = "0" + phone.substring(2);
        }
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw badRequest("Số điện thoại không hợp lệ");
        }
        if (!SUPPORTED_PHONE_PREFIXES.contains(phone.substring(0, 3))) {
            throw badRequest("Số điện thoại không thuộc nhà mạng được hỗ trợ");
        }
        return phone;
    }

    private String normalizeGender(String rawValue) {
        if (!StringUtils.hasText(rawValue)) return null;
        return normalizeChoice(rawValue, GENDERS, "Gender");
    }

    private String normalizeOptionalText(String rawValue) {
        if (!StringUtils.hasText(rawValue)) return null;
        String value = rawValue.trim();
        if (value.length() > 255) throw badRequest("Address không hợp lệ");
        return value;
    }

    private String validatePassword(String password, String confirmPassword) {
        if (password == null || password.length() < 8 || password.length() > 72
                || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw badRequest("Password không hợp lệ");
        }
        if (!password.equals(confirmPassword)) {
            throw badRequest("Confirm password không khớp");
        }
        return password;
    }

    private String normalizeSearch(String search) {
        if (!StringUtils.hasText(search)) return null;
        String keyword = search.trim();
        if (keyword.length() > 100) throw badRequest("Search must not exceed 100 characters");
        return keyword;
    }

    private String normalizeOptionalChoice(String rawValue, List<String> choices, String label) {
        if (!StringUtils.hasText(rawValue) || "all".equalsIgnoreCase(rawValue.trim())) return null;
        return normalizeChoice(rawValue, choices, label);
    }

    private String normalizeChoice(String rawValue, List<String> choices, String label) {
        String value = rawValue == null ? "" : rawValue.trim();
        return choices.stream()
                .filter(choice -> choice.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> badRequest(label + " không hợp lệ"));
    }

    private Integer parseUserId(String keyword) {
        if (!StringUtils.hasText(keyword)) return null;
        String value = keyword.trim().toUpperCase(Locale.ROOT);
        for (String prefix : List.of("UID-", "USR-", "USER-")) {
            if (value.startsWith(prefix)) {
                value = value.substring(prefix.length());
                break;
            }
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private String csvCell(String rawValue) {
        String value = rawValue == null ? "" : rawValue;
        if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private int roleOrder(String roleName) {
        for (int index = 0; index < MANAGED_ROLES.size(); index++) {
            if (MANAGED_ROLES.get(index).equalsIgnoreCase(roleName)) return index;
        }
        return Integer.MAX_VALUE;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
