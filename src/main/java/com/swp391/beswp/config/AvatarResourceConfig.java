package com.swp391.beswp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class AvatarResourceConfig implements WebMvcConfigurer {

    private final Path avatarDirectory;

    public AvatarResourceConfig(
            @Value("${app.upload.avatar-dir:uploads/avatars}") String avatarDirectory
    ) {
        this.avatarDirectory = Paths.get(avatarDirectory).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = avatarDirectory.toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/uploads/avatars/**")
                .addResourceLocations(location);
    }

    @Bean
    @Order(0)
    public SecurityFilterChain publicAvatarResources(HttpSecurity http) throws Exception {
        http.securityMatcher("/uploads/avatars/**")
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/uploads/avatars/**").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/uploads/avatars/**").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }
}
