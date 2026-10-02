package com.swp391.beswp.service;

import com.swp391.beswp.dto.LoginRequest;
import com.swp391.beswp.dto.LoginResponse;
import com.swp391.beswp.dto.RegisterRequest;
import com.swp391.beswp.dto.RegisterResponse;
import com.swp391.beswp.dto.UserResponse;
import com.swp391.beswp.entity.Role;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.repository.RoleRepository;
import com.swp391.beswp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Optional;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE =
            "Ten tai khoan, email hoac mat khau khong chinh xac";
    private static final String LOGIN_NOT_ALLOWED_MESSAGE =
            "Tai khoan khong duoc phep dang nhap";
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[a-z0-9._]+$");
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^0\\d{9}$");
    private static final Set<String> SUPPORTED_PHONE_PREFIXES = Set.of(
            "032", "033", "034", "035", "036", "037", "038", "039", "086", "096", "097", "098",
            "070", "076", "077", "078", "079", "089", "090", "093",
            "081", "082", "083", "084", "085", "088", "091", "094"
    );
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String identifier = normalizeIdentifier(request.getIdentifier());
        User user = findUser(identifier)
                .orElseThrow(this::invalidCredentials);

        Role role = user.getRole();
        if (!isActive(user) || role == null || !StringUtils.hasText(role.getRoleName())) {
            throw loginNotAllowed();
        }

        if (!StringUtils.hasText(user.getPassword())
                || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw invalidCredentials();
        }

        String accessToken = jwtService.generateToken(user);
        UserResponse userResponse = new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getUsername(),
                user.getEmail(),
                role.getRoleName(),
                user.getStatus()
        );
        return LoginResponse.success(accessToken, jwtService.getExpirationSeconds(), userResponse);
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String username = normalizeUsername(request.getUsername());
        String fullName = normalizeFullName(request.getFullName());
        String email = normalizeEmail(request.getEmail());
        String phone = normalizePhone(request.getPhone());
        validatePassword(request.getPassword(), request.getConfirmPassword());

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw conflict("Username da ton tai");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw conflict("Email da ton tai");
        }
        if (userRepository.existsByPhone(phone)) {
            throw conflict("Phone da ton tai");
        }

        Role memberRole = roleRepository.findByRoleNameIgnoreCase("Member")
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Member role is not configured"
                ));

        User user = new User();
        user.setUsername(username);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(memberRole);
        user.setStatus("Active");

        User savedUser = userRepository.save(user);
        return RegisterResponse.success(new RegisterResponse.RegisterUserData(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getFullName(),
                savedUser.getEmail(),
                savedUser.getPhone(),
                memberRole.getRoleName(),
                savedUser.getStatus()
        ));
    }

    @Transactional
    public LoginResponse loginWithGoogle(String rawEmail, String googleName, String avatarUrl) {
        String email = normalizeEmail(rawEmail);
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> createGoogleMemberSafely(email, googleName, avatarUrl));

        Role role = user.getRole();
        if (!isActive(user) || role == null || !StringUtils.hasText(role.getRoleName())) {
            throw loginNotAllowed();
        }

        String accessToken = jwtService.generateToken(user);
        UserResponse userResponse = new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getUsername(),
                user.getEmail(),
                role.getRoleName(),
                user.getStatus()
        );
        return LoginResponse.success(accessToken, jwtService.getExpirationSeconds(), userResponse);
    }

    private User createGoogleMemberSafely(String email, String googleName, String avatarUrl) {
        try {
            return createGoogleMember(email, googleName, avatarUrl);
        } catch (DataIntegrityViolationException ex) {
            return userRepository.findByEmailIgnoreCase(email).orElseThrow(() -> ex);
        }
    }

    private User createGoogleMember(String email, String googleName, String avatarUrl) {
        Role memberRole = roleRepository.findByRoleNameIgnoreCase("Member")
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Member role is not configured"
                ));

        User user = new User();
        user.setUsername(generateGoogleUsername(email));
        user.setFullName(normalizeGoogleFullName(googleName, email));
        user.setEmail(email);
        user.setAvatarUrl(StringUtils.hasText(avatarUrl) ? avatarUrl : null);
        user.setPassword(passwordEncoder.encode(randomPassword()));
        user.setRole(memberRole);
        user.setStatus("Active");

        return userRepository.saveAndFlush(user);
    }

    private String generateGoogleUsername(String email) {
        String localPart = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT);
        String base = localPart.replaceAll("[^a-z0-9._]", ".")
                .replaceAll("\\.{2,}", ".")
                .replaceAll("^\\.|\\.$", "");
        if (!StringUtils.hasText(base)) {
            base = "google.user";
        }
        base = base.substring(0, Math.min(base.length(), 40));

        String candidate = base;
        while (userRepository.existsByUsernameIgnoreCase(candidate)) {
            candidate = base + "." + randomSuffix();
        }
        return candidate;
    }

    private String normalizeGoogleFullName(String googleName, String email) {
        if (StringUtils.hasText(googleName) && googleName.trim().length() <= 100) {
            return googleName.trim();
        }
        return email.substring(0, email.indexOf('@'));
    }

    private String randomPassword() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "Aa1!";
    }

    private String randomSuffix() {
        byte[] bytes = new byte[5];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "a");
    }

    private String normalizeIdentifier(String rawIdentifier) {
        String identifier = rawIdentifier.trim().toLowerCase(Locale.ROOT);
        if (identifier.contains("@")) {
            if (!EMAIL_PATTERN.matcher(identifier).matches()) {
                throw badRequest("Email khong hop le");
            }
            return identifier;
        }

        if (!USERNAME_PATTERN.matcher(identifier).matches()) {
            throw badRequest("Username khong hop le");
        }
        return identifier;
    }

    private Optional<User> findUser(String identifier) {
        if (identifier.contains("@")) {
            return userRepository.findByEmailIgnoreCase(identifier);
        }
        return userRepository.findByUsernameIgnoreCase(identifier);
    }

    private String normalizeUsername(String rawUsername) {
        String username = rawUsername.trim().toLowerCase(Locale.ROOT);
        if (username.length() > 50 || !USERNAME_PATTERN.matcher(username).matches()) {
            throw badRequest("Username khong hop le");
        }
        return username;
    }

    private String normalizeFullName(String rawFullName) {
        String fullName = rawFullName.trim();
        if (!StringUtils.hasText(fullName) || fullName.length() > 100) {
            throw badRequest("Full name khong hop le");
        }
        return fullName;
    }

    private String normalizeEmail(String rawEmail) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 100 || !EMAIL_PATTERN.matcher(email).matches()) {
            throw badRequest("Email khong hop le");
        }
        return email;
    }

    private String normalizePhone(String rawPhone) {
        String phone = rawPhone.trim()
                .replace(" ", "")
                .replace(".", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "");
        if (phone.startsWith("+84")) {
            phone = "0" + phone.substring(3);
        } else if (phone.startsWith("84") && phone.length() == 11) {
            phone = "0" + phone.substring(2);
        }

        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw badRequest("So dien thoai khong hop le");
        }
        if (!SUPPORTED_PHONE_PREFIXES.contains(phone.substring(0, 3))) {
            throw badRequest("So dien thoai khong thuoc nha mang duoc ho tro");
        }
        return phone;
    }

    private void validatePassword(String password, String confirmPassword) {
        if (password.length() < 8 || password.length() > 72 || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw badRequest("Password khong hop le");
        }
        if (!password.equals(confirmPassword)) {
            throw badRequest("Confirm password khong khop");
        }
    }

    private boolean isActive(User user) {
        return "Active".equalsIgnoreCase(user.getStatus());
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE);
    }

    private ResponseStatusException loginNotAllowed() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, LOGIN_NOT_ALLOWED_MESSAGE);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
