package com.priceradar.operations.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class ActuatorSecurityConfiguration {

    private static final String ACTUATOR_ROLE = "ACTUATOR";

    @Bean
    public PasswordEncoder actuatorPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService actuatorUserDetailsService(
            @Value("${priceradar.operations.actuator.username}") String username,
            @Value("${priceradar.operations.actuator.password}") String password,
            PasswordEncoder passwordEncoder
    ) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("Actuator credentials must not be blank");
        }
        UserDetails actuatorUser = User.withUsername(username.trim())
                .password(passwordEncoder.encode(password))
                .roles(ACTUATOR_ROLE)
                .build();
        return new InMemoryUserDetailsManager(actuatorUser);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().hasRole(ACTUATOR_ROLE))
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain denyUnknownHttpEndpoints(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().denyAll())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
