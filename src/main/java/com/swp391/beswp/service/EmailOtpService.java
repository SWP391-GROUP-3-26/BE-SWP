package com.swp391.beswp.service;

import com.swp391.beswp.dto.EmailOtpDtos.*;
import com.swp391.beswp.dto.RegisterResponse;
import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EmailOtpService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final EmailOtpChallengeRepository challenges;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final OtpMailService mail;
    private final AuditLogService audit;
    private final ApplicationEventPublisher events;
    private final Clock paymentClock;

    @Transactional
    public Challenge startRegistration(User pending) {
        EmailOtpChallenge row = lockOrCreate(pending.getEmail());
        assertSendAllowed(row);
        String token = begin(row, "REGISTER", null);
        row.setPendingUsername(pending.getUsername()); row.setPendingFullName(pending.getFullName());
        row.setPendingPhone(pending.getPhone()); row.setPendingPasswordHash(pending.getPassword());
        send(row);
        return challenge(row, token);
    }

    /** Password was checked by AuthService. This token grants only the email verification flow. */
    @Transactional
    public Challenge loginChallenge(User user) {
        EmailOtpChallenge row = lockOrCreate(normalizeStoredEmail(user.getEmail()));
        String token = begin(row, "VERIFY_EMAIL", user);
        return challenge(row, token);
    }

    @Transactional
    public Challenge requestVerificationEmailChange(VerificationEmailChangeRequest request) {
        String originalEmail = normalizeStoredEmail(request.email()), newEmail = normalize(request.newEmail());
        if (originalEmail.equals(newEmail)) throw error(HttpStatus.BAD_REQUEST, "Email mới phải khác email hiện tại");
        Map<String, EmailOtpChallenge> rows = lockEmailPair(originalEmail, newEmail, false);
        EmailOtpChallenge original = context(originalEmail, request.verificationToken(), "VERIFY_EMAIL");
        User user = currentUser(original, false);
        assertEmailChangeState(user, "VERIFY_CHANGE_EMAIL", null);
        Challenge next = startEmailChange(rows.get(newEmail), user, newEmail, "VERIFY_CHANGE_EMAIL");
        // Consume the password-authenticated login grant only after SMTP accepts the new OTP.
        clear(original);
        return next;
    }

    @Transactional
    public Challenge requestProfileEmailChange(Integer userId, ProfileEmailChangeRequest request) {
        String originalEmail = normalizeStoredEmail(users.findEmailById(userId).orElseThrow(this::invalid));
        String newEmail = normalize(request.newEmail());
        if (originalEmail.equals(newEmail)) throw error(HttpStatus.BAD_REQUEST, "Email mới phải khác email hiện tại");
        Map<String, EmailOtpChallenge> rows = lockEmailPair(originalEmail, newEmail, true);
        User user = users.findByIdForUpdate(userId).orElseThrow(this::invalid);
        if (!originalEmail.equals(normalizeStoredEmail(user.getEmail()))) throw error(HttpStatus.CONFLICT, "Email vừa thay đổi; vui lòng tải lại hồ sơ");
        assertEmailChangeState(user, "CHANGE_EMAIL", userId);
        if (user.getPassword() == null || !encoder.matches(request.currentPassword(), user.getPassword()))
            throw error(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không chính xác");
        return startEmailChange(rows.get(newEmail), user, newEmail, "CHANGE_EMAIL");
    }

    @Transactional
    public void resendEmailChange(ContextRequest request, String purpose, Integer authenticatedUserId) {
        EmailOtpChallenge row = context(request.email(), request.verificationToken(), purpose);
        User user = currentUser(row, false);
        assertEmailChangeState(user, purpose, authenticatedUserId);
        assertEmailAvailable(row.getEmail());
        assertSendAllowed(row);
        send(row);
    }

    @Transactional(noRollbackFor = OtpRejectedException.class)
    public User verifyEmailChange(VerifyRequest request, String purpose, Integer authenticatedUserId) {
        EmailOtpChallenge row = context(request.email(), request.verificationToken(), purpose);
        User user = currentUser(row, false);
        assertEmailChangeState(user, purpose, authenticatedUserId);
        verifyOtp(row, request.otp());
        assertEmailAvailable(row.getEmail());
        String previousEmail = user.getEmail();
        user.setEmail(row.getEmail()); user.setEmailVerified(true);
        user.setSessionVersion(user.getSessionVersion() + 1);
        // Keep the unique email constraint as the final defense against a concurrent registration/change.
        users.saveAndFlush(user);
        clear(row);
        audit.recordLog(user, "CHANGE_EMAIL", "User", "Đổi email tài khoản #" + user.getId()
                + " từ " + previousEmail + " thành " + user.getEmail() + " sau xác thực OTP");
        events.publishEvent(new AccountSessionsInvalidated(user.getId()));
        return user;
    }

    private Challenge startEmailChange(EmailOtpChallenge row, User user, String newEmail, String purpose) {
        assertEmailAvailable(newEmail);
        assertSendAllowed(row);
        String token = begin(row, purpose, user);
        row.setOriginalEmail(normalizeStoredEmail(user.getEmail()));
        send(row);
        return challenge(row, token);
    }

    private void assertEmailAvailable(String email) {
        if (users.existsByEmailIgnoreCase(email)) throw error(HttpStatus.CONFLICT, "Email đã được sử dụng");
    }

    private void assertEmailChangeState(User user, String purpose, Integer authenticatedUserId) {
        if (!"Active".equalsIgnoreCase(user.getStatus()) || user.getRole() == null) throw invalid();
        if (!Set.of("LOCAL", "UNKNOWN").contains(user.getAuthProvider()))
            throw error(HttpStatus.FORBIDDEN, "Tài khoản Google phải giữ email Google");
        if ("VERIFY_CHANGE_EMAIL".equals(purpose)) {
            if (authenticatedUserId != null || user.isEmailVerified() || !"Member".equalsIgnoreCase(user.getRole().getRoleName())) throw invalid();
        } else if ("CHANGE_EMAIL".equals(purpose)) {
            if (!user.isEmailVerified() || !Objects.equals(authenticatedUserId, user.getId())) throw invalid();
        } else throw invalid();
    }

    private Map<String, EmailOtpChallenge> lockEmailPair(String original, String target, boolean createOriginal) {
        Map<String, EmailOtpChallenge> rows = new HashMap<>();
        // Consistent order prevents A -> B and B -> A requests from locking email rows in reverse order.
        for (String email : java.util.stream.Stream.of(original, target).sorted().toList()) {
            rows.put(email, email.equals(original) && !createOriginal
                    ? challenges.lockByEmail(email).orElseThrow(this::invalid) : lockOrCreate(email));
        }
        return rows;
    }

    @Transactional
    public Challenge requestPasswordReset(String email) {
        email = normalize(email);
        EmailOtpChallenge row = lockOrCreate(email);
        Integer userId = users.findIdByEmailIgnoreCase(email).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Email không tồn tại"));
        User user = users.findByIdForUpdate(userId).orElseThrow(this::invalid);
        if (!email.equals(normalize(user.getEmail()))) throw invalid();
        assertResetAllowed(user);
        assertSendAllowed(row);
        String token = begin(row, "RESET", user);
        send(row);
        return challenge(row, token);
    }

    @Transactional
    public void resend(ContextRequest request, String purpose) {
        EmailOtpChallenge row = context(request.email(), request.verificationToken(), purpose);
        if (row.getUserId() != null) currentUser(row, "RESET".equals(purpose));
        if (row.getOtpHash() == null && "RESET".equals(purpose)) throw invalid();
        assertSendAllowed(row);
        send(row);
    }

    @Transactional(noRollbackFor = OtpRejectedException.class)
    public RegisterResponse verifyRegistration(VerifyRequest request) {
        EmailOtpChallenge row = context(request.email(), request.verificationToken(), "REGISTER");
        verifyOtp(row, request.otp());
        if (users.existsByEmailIgnoreCase(row.getEmail()) || users.existsByUsernameIgnoreCase(row.getPendingUsername())
                || users.existsByPhone(row.getPendingPhone())) {
            throw error(HttpStatus.CONFLICT, "Email, username hoặc số điện thoại đã được sử dụng; vui lòng đăng ký lại");
        }
        User user = new User();
        user.setEmail(row.getEmail()); user.setUsername(row.getPendingUsername());
        user.setFullName(row.getPendingFullName()); user.setPhone(row.getPendingPhone());
        user.setPassword(row.getPendingPasswordHash()); user.setEmailVerified(true); user.setAuthProvider("LOCAL");
        user.setStatus("Active");
        user.setRole(roles.findByRoleNameIgnoreCase("Member").orElseThrow(() -> error(HttpStatus.INTERNAL_SERVER_ERROR, "Member role is not configured")));
        users.saveAndFlush(user);
        clear(row);
        audit.recordLog(user, "REGISTER_USER", "User", "Người dùng tự đăng ký tài khoản trực tuyến #" + user.getId() + " sau xác thực email");
        return RegisterResponse.success(new RegisterResponse.RegisterUserData(user.getId(), user.getUsername(),
                user.getFullName(), user.getEmail(), user.getPhone(), "Member", user.getStatus()));
    }

    @Transactional(noRollbackFor = OtpRejectedException.class)
    public User verifyMember(VerifyRequest request) {
        EmailOtpChallenge row = context(request.email(), request.verificationToken(), "VERIFY_EMAIL");
        User user = currentUser(row, false);
        if (!"Member".equalsIgnoreCase(user.getRole().getRoleName())) throw invalid();
        verifyOtp(row, request.otp());
        user.setEmailVerified(true);
        clear(row);
        audit.recordLog(user, "VERIFY_EMAIL", "User", "Xác thực email tài khoản #" + user.getId());
        return user;
    }

    @Transactional(noRollbackFor = OtpRejectedException.class)
    public ResetGrant verifyReset(VerifyRequest request) {
        EmailOtpChallenge row = context(request.email(), request.verificationToken(), "RESET");
        currentUser(row, true);
        verifyOtp(row, request.otp());
        String token = randomToken();
        row.setOtpHash(null); row.setContextHash(null);
        row.setResetHash(hash(token)); row.setResetExpiresAt(now().plusSeconds(300));
        return new ResetGrant(token, row.getResetExpiresAt());
    }

    @Transactional
    public void resetPassword(ResetRequest request) {
        AuthService.validatePassword(request.password(), request.confirmPassword());
        EmailOtpChallenge row = challenges.lockByEmail(normalize(request.email())).orElseThrow(this::invalid);
        if (row.getResetHash() == null || !row.getResetHash().equals(hash(request.resetToken()))
                || !now().isBefore(row.getResetExpiresAt())) throw invalid();
        User user = currentUser(row, true);
        user.setPassword(encoder.encode(request.password()));
        user.setSessionVersion(user.getSessionVersion() + 1);
        // Resetting a password does not bypass the Member email-verification gate.
        clear(row);
        audit.recordLog(user, "RESET_PASSWORD", "User", "Đặt lại mật khẩu qua email cho tài khoản #" + user.getId());
        events.publishEvent(new AccountSessionsInvalidated(user.getId()));
    }

    @Scheduled(fixedDelayString = "${app.otp.cleanup-delay-ms:3600000}")
    @Transactional
    public void cleanExpired() { challenges.deleteExpired(now().minusSeconds(3600), now()); }

    private EmailOtpChallenge lockOrCreate(String email) {
        return challenges.lockByEmail(email).orElseGet(() -> {
            EmailOtpChallenge row = new EmailOtpChallenge(); row.setEmail(email);
            // A concurrent first request may hit the unique key and return 409; it cannot bypass limits.
            return challenges.saveAndFlush(row);
        });
    }
    private String begin(EmailOtpChallenge row, String purpose, User user) {
        clear(row);
        String token = randomToken();
        row.setPurpose(purpose); row.setContextHash(hash(token)); row.setContextExpiresAt(now().plusSeconds(900));
        if (user != null) { row.setUserId(user.getId()); row.setSessionVersion(user.getSessionVersion()); }
        return token;
    }
    private void clear(EmailOtpChallenge row) {
        row.setPurpose(null); row.setContextHash(null); row.setContextExpiresAt(null);
        row.setUserId(null); row.setSessionVersion(null); row.setOtpHash(null); row.setOtpExpiresAt(null);
        row.setOriginalEmail(null);
        row.setPendingUsername(null); row.setPendingFullName(null); row.setPendingPhone(null); row.setPendingPasswordHash(null);
        row.setResetHash(null); row.setResetExpiresAt(null); row.setFailedAttempts(0);
        // Keep last send and hourly budget even after success, resend, or a new login/request.
    }
    private void assertSendAllowed(EmailOtpChallenge row) {
        Instant now = now();
        if (row.getLastSentAt() != null && now.isBefore(row.getLastSentAt().plusSeconds(60)))
            throw error(HttpStatus.TOO_MANY_REQUESTS, "Vui lòng chờ 60 giây giữa hai lần gửi OTP");
        if (recentSends(row).size() >= 5)
            throw error(HttpStatus.TOO_MANY_REQUESTS, "Đã đạt giới hạn 5 lần gửi OTP/email trong một giờ");
    }
    private void send(EmailOtpChallenge row) {
        Instant now = now();
        List<Instant> sends = recentSends(row);
        sends.add(now);
        row.setWindowStartedAt(sends.getFirst()); row.setSendCount(sends.size());
        row.setSendHistory(String.join(",", sends.stream().map(i -> Long.toString(i.toEpochMilli())).toList()));
        String otp;
        do { otp = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000)); }
        while (row.getOtpHash() != null && encoder.matches(otp, row.getOtpHash()));
        row.setOtpHash(encoder.encode(otp)); row.setOtpExpiresAt(now.plusSeconds(300)); row.setFailedAttempts(0);
        row.setLastSentAt(now);
        mail.send(row.getEmail(), row.getPurpose(), otp);
    }
    private EmailOtpChallenge context(String email, String token, String purpose) {
        EmailOtpChallenge row = challenges.lockByEmail(normalizeStoredEmail(email)).orElseThrow(this::invalid);
        if (!purpose.equals(row.getPurpose()) || row.getContextHash() == null || !row.getContextHash().equals(hash(token))
                || !now().isBefore(row.getContextExpiresAt())) throw invalid();
        return row;
    }
    private List<Instant> recentSends(EmailOtpChallenge row) {
        if (row.getSendHistory() == null || row.getSendHistory().isBlank()) return new ArrayList<>();
        Instant cutoff = now().minusSeconds(3600);
        return new ArrayList<>(Arrays.stream(row.getSendHistory().split(","))
                .map(s -> Instant.ofEpochMilli(Long.parseLong(s))).filter(i -> i.isAfter(cutoff)).toList());
    }
    private User currentUser(EmailOtpChallenge row, boolean reset) {
        User user = users.findByIdForUpdate(row.getUserId()).orElseThrow(this::invalid);
        String expectedEmail = Set.of("CHANGE_EMAIL", "VERIFY_CHANGE_EMAIL").contains(row.getPurpose())
                ? row.getOriginalEmail() : row.getEmail();
        if (!"Active".equalsIgnoreCase(user.getStatus()) || !Objects.equals(expectedEmail, normalizeStoredEmail(user.getEmail()))
                || !Objects.equals(row.getSessionVersion(), user.getSessionVersion()) || user.getRole() == null) throw invalid();
        if (reset) assertResetAllowed(user);
        return user;
    }
    private void assertResetAllowed(User user) {
        if (!"Active".equalsIgnoreCase(user.getStatus())) throw error(HttpStatus.FORBIDDEN, "Tài khoản không được phép khôi phục");
        if ("GOOGLE".equals(user.getAuthProvider())) throw error(HttpStatus.FORBIDDEN, "Vui lòng đăng nhập bằng Google");
        if (!"LOCAL".equals(user.getAuthProvider())) throw error(HttpStatus.CONFLICT, "Nguồn tài khoản chưa xác định; vui lòng liên hệ quản trị viên");
    }
    private void verifyOtp(EmailOtpChallenge row, String otp) {
        if (row.getOtpHash() == null || !now().isBefore(row.getOtpExpiresAt()) || row.getFailedAttempts() >= 5)
            throw new OtpRejectedException("OTP đã hết hạn, đã sử dụng hoặc đã vượt số lần thử");
        if (otp == null || !otp.matches("[0-9]{6}") || !encoder.matches(otp, row.getOtpHash())) {
            row.setFailedAttempts(row.getFailedAttempts() + 1);
            throw new OtpRejectedException("OTP không chính xác");
        }
    }
    private Challenge challenge(EmailOtpChallenge row, String token) {
        return new Challenge(row.getEmail(), token, row.getContextExpiresAt(), 300,
                row.getLastSentAt() == null ? 0 : (int)Math.max(0, Duration.between(now(), row.getLastSentAt().plusSeconds(60)).getSeconds()));
    }
    private String normalize(String email) {
        if (email == null || email.length() > 100 || !email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw error(HttpStatus.BAD_REQUEST, "Email không hợp lệ");
        return email.trim().toLowerCase(Locale.ROOT);
    }
    private String normalizeStoredEmail(String email) {
        if (email == null || email.isBlank() || email.trim().length() > 100) throw invalid();
        return email.trim().toLowerCase(Locale.ROOT);
    }
    private Instant now() { return paymentClock.instant(); }
    private String randomToken() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private ResponseStatusException invalid() { return error(HttpStatus.BAD_REQUEST, "Yêu cầu xác thực không hợp lệ hoặc đã hết hạn"); }
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
    public static class OtpRejectedException extends ResponseStatusException {
        public OtpRejectedException(String message) { super(HttpStatus.BAD_REQUEST, message); }
    }
}
