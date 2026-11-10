package com.swp391.beswp.service;

import com.swp391.beswp.entity.User;
import com.swp391.beswp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @RequiredArgsConstructor
public class AccountIdentityService {
    private final UserRepository users;
    private final AuditLogService audit;
    private final ApplicationEventPublisher events;
    public record Identity(Integer userId, String username, String email, String role, String authProvider, boolean emailVerified) {
        static Identity from(User user) { return new Identity(user.getId(), user.getUsername(), user.getEmail(),
                user.getRole().getRoleName(), user.getAuthProvider(), user.isEmailVerified()); }
    }
    @Transactional(readOnly = true)
    public Page<Identity> unresolved(int page, int size) {
        return users.findByAuthProvider("UNKNOWN", PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100), Sort.by("id")))
                .map(Identity::from);
    }
    @Transactional
    public Identity resolve(Integer id, String provider, String reason) {
        if ((!"LOCAL".equals(provider) && !"GOOGLE".equals(provider)) || reason == null || reason.isBlank() || reason.length() > 500)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nguồn tài khoản hoặc lý do không hợp lệ");
        User user = users.findByIdForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản"));
        if (!"UNKNOWN".equals(user.getAuthProvider())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Tài khoản đã xác định nguồn");
        user.setAuthProvider(provider); user.setSessionVersion(user.getSessionVersion() + 1);
        // Source review does not prove ownership of an email address.
        audit.recordLog(null, "UPDATE_AUTH_PROVIDER", "User", "Rà soát nguồn tài khoản #" + id + " thành " + provider + ": " + reason.trim());
        events.publishEvent(new AccountSessionsInvalidated(id));
        return Identity.from(user);
    }
}
