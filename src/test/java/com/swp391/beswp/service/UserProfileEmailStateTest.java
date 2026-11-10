package com.swp391.beswp.service;

import com.swp391.beswp.dto.UpdateUserProfileRequest;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UserProfileEmailStateTest {
    UserRepository users = mock(UserRepository.class);
    UserProfileService service = new UserProfileService(users);
    @Test void profileExposesVerificationSourceAndWhetherEmailCanBeChanged() {
        User user = new User(); user.setStatus("Active"); user.setEmailVerified(true);
        when(users.findById(7)).thenReturn(Optional.of(user));
        assertTrue(service.getMyProfile("7").getData().isCanChangeEmail());
        user.setAuthProvider("GOOGLE"); assertFalse(service.getMyProfile("7").getData().isCanChangeEmail());
        user.setAuthProvider("UNKNOWN"); assertTrue(service.getMyProfile("7").getData().isCanChangeEmail());
        user.setEmailVerified(false); assertFalse(service.getMyProfile("7").getData().isCanChangeEmail());
    }
    @Test void ordinaryProfileUpdatePreservesVerifiedEmailAndSessionVersion() {
        User user = new User(); user.setEmail("verified@example.com"); user.setEmailVerified(true); user.setSessionVersion(9);
        when(users.findByIdForUpdate(7)).thenReturn(Optional.of(user)); when(users.save(any())).thenReturn(user);
        UpdateUserProfileRequest request = new UpdateUserProfileRequest(); request.setFullName("Member");
        var response = service.updateMyProfile("7", request);
        assertEquals("verified@example.com", response.getData().getEmail()); assertTrue(response.getData().isEmailVerified());
        assertEquals(9, user.getSessionVersion()); verify(users, never()).findById(anyInt());
    }
}
