package com.swp391.beswp.config;

import com.swp391.beswp.dto.LoginResponse;
import com.swp391.beswp.service.AuthService;
import com.swp391.beswp.service.GoogleLoginExchangeService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class GoogleOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final GoogleLoginExchangeService exchangeService;

    @Value("${app.oauth2.google.success-redirect}")
    private String successRedirect;

    @Value("${app.oauth2.google.failure-redirect}")
    private String failureRedirect;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        try {
            OAuth2User googleUser = (OAuth2User) authentication.getPrincipal();
            String email = googleUser.getAttribute("email");
            if (!StringUtils.hasText(email) || !isEmailVerified(googleUser.getAttribute("email_verified"))) {
                redirect(response, failureRedirect, "error", "google_email_not_verified");
                return;
            }

            LoginResponse loginResponse = authService.loginWithGoogle(
                    email,
                    googleUser.getAttribute("name"),
                    googleUser.getAttribute("picture")
            );
            String code = exchangeService.issue(loginResponse);
            redirect(response, successRedirect, "code", code);
        } catch (RuntimeException ex) {
            redirect(response, failureRedirect, "error", "google_login_failed");
        }
    }

    private boolean isEmailVerified(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private void redirect(HttpServletResponse response, String baseUrl, String name, String value) throws IOException {
        String separator = baseUrl.contains("?") ? "&" : "?";
        response.sendRedirect(baseUrl + separator + name + "="
                + URLEncoder.encode(value, StandardCharsets.UTF_8));
    }
}
