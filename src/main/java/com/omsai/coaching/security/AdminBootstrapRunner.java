package com.omsai.coaching.security;

import com.omsai.coaching.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time creation of the first administrator account.
 *
 * <p>Activation is opt-in via environment configuration (see README.md):
 * {@code APP_BOOTSTRAP_ADMIN=true} together with {@code APP_ADMIN_NAME},
 * {@code APP_ADMIN_EMAIL} and {@code APP_ADMIN_PASSWORD}. The runner is
 * idempotent: it does nothing when an administrator already exists.
 * The password is supplied through the environment, never committed to
 * Git, and stored only as a BCrypt hash.</p>
 */
@Component
public class AdminBootstrapRunner implements CommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    public AdminBootstrapRunner(UserAccountRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                AppProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(String... args) {
        AppProperties.Admin config = properties.getBootstrap().getAdmin();
        if (!config.isEnabled()) {
            return;
        }
        if (config.getEmail() == null || config.getEmail().isBlank()
                || config.getPassword() == null || config.getPassword().isBlank()
                || config.getName() == null || config.getName().isBlank()) {
            LOG.error("Admin bootstrap enabled but APP_ADMIN_NAME, APP_ADMIN_EMAIL or "
                    + "APP_ADMIN_PASSWORD is missing. No account was created.");
            return;
        }
        if (config.getPassword().length() < 12) {
            LOG.error("Admin bootstrap password must be at least 12 characters. No account was created.");
            return;
        }
        if (userRepository.existsByRole(UserAccount.Role.ADMIN)) {
            LOG.info("Admin bootstrap skipped: an administrator account already exists.");
            return;
        }
        if (userRepository.existsByEmailIgnoreCase(config.getEmail())) {
            LOG.error("Admin bootstrap skipped: an account with email {} already exists.", config.getEmail());
            return;
        }

        UserAccount admin = new UserAccount();
        admin.setFullName(config.getName().trim());
        admin.setEmail(config.getEmail().trim());
        admin.setPasswordHash(passwordEncoder.encode(config.getPassword()));
        admin.setRole(UserAccount.Role.ADMIN);
        admin.setActive(true);
        userRepository.save(admin);
        LOG.info("First administrator account created for {}. "
                + "Please sign in and change this password soon.", config.getEmail());
    }
}
