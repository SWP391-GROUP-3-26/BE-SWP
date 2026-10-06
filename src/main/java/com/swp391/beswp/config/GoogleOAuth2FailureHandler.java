package com.swp391.beswp.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class GoogleOAuth2FailureHandler implements AuthenticationFailureHandler {

    @Value("${app.oauth2.google.failure-redirect}")
    private String failureRedirect;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        String separator = failureRedirect.contains("?") ? "&" : "?";
        response.sendRedirect(failureRedirect + separator + "error="
                + URLEncoder.encode("google_authentication_failed", StandardCharsets.UTF_8));
    }
}
