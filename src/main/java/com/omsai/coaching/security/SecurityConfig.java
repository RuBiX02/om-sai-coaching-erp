package com.omsai.coaching.security;

import com.omsai.coaching.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Session-based security for the server-rendered application.
 *
 * <p>All management features require the ADMIN role. Teacher accounts
 * may reach the read-only teacher home page and the class-session
 * module limited to their own assigned subjects. Fee and payment
 * pages are administrator-only. CSRF protection is enabled (Thymeleaf
 * forms automatically carry the CSRF token) and strict security
 * headers are applied.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder(AppProperties properties) {
        return new BCryptPasswordEncoder(properties.getSecurity().getBcryptStrength());
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                          LoginRateLimitFilter loginRateLimitFilter) throws Exception {
        http
                .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/fonts/**", "/images/**",
                                "/favicon.ico", "/favicon.svg", "/login", "/error/**").permitAll()
                        .requestMatchers("/").authenticated()
                        .requestMatchers("/sessions/**").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers("/timetable/**").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers("/examinations/**").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers("/teacher/**").hasRole("TEACHER")
                        .requestMatchers("/student/**").hasRole("STUDENT")
                        .anyRequest().hasRole("ADMIN")
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", false)
                        .failureUrl("/login?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .permitAll()
                )
                .exceptionHandling(ex -> ex.accessDeniedPage("/error/403"))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; img-src 'self' data:; style-src 'self'; "
                                        + "script-src 'self'; frame-ancestors 'none'; base-uri 'self'"))
                        .referrerPolicy(rp -> rp.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true))
                );
        return http.build();
    }
}
