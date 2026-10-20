package com.omsai.coaching.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end login behaviour over HTTP: successful sign-in, bad
 * credentials and the username + IP lockout after repeated failures.
 * Each test uses a unique account so the shared in-memory rate limiter
 * state cannot leak between tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LoginWebTest {

    private static final String PASSWORD = "Good#Password#2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void successfulLoginRedirectsToHome() throws Exception {
        String email = uniqueEmail("login.ok");
        saveAdmin(email);

        mockMvc.perform(formLogin().user(email).password(PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void wrongPasswordRedirectsBackWithError() throws Exception {
        String email = uniqueEmail("login.bad");
        saveAdmin(email);

        mockMvc.perform(formLogin().user(email).password("Wrong#Password#2026"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void unknownUserRedirectsBackWithError() throws Exception {
        String email = uniqueEmail("login.ghost");

        mockMvc.perform(formLogin().user(email).password("Wrong#Password#2026"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void sixthFailureWithinWindowIsLockedOut() throws Exception {
        String email = uniqueEmail("login.locked");
        saveAdmin(email);

        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(formLogin().user(email).password("Wrong#Password#2026"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login?error"));
        }

        // The sixth attempt is rejected by the rate limit filter before
        // any password check runs, even with the correct credentials.
        mockMvc.perform(formLogin().user(email).password(PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?locked"));
    }

    private void saveAdmin(String email) {
        UserAccount user = new UserAccount();
        user.setFullName("Login Test Admin");
        user.setEmail(email);
        user.setRole(UserAccount.Role.ADMIN);
        user.setActive(true);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userAccountRepository.save(user);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "." + UUID.randomUUID() + "@omsai.local";
    }
}
