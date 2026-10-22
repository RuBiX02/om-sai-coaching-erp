package com.omsai.coaching.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application-level configuration properties (prefix "app").
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Security security = new Security();
    private final LoginRateLimit loginRateLimit = new LoginRateLimit();
    private final Bootstrap bootstrap = new Bootstrap();
    private final Demo demo = new Demo();

    public Security getSecurity() {
        return security;
    }

    public LoginRateLimit getLoginRateLimit() {
        return loginRateLimit;
    }

    public Bootstrap getBootstrap() {
        return bootstrap;
    }

    public Demo getDemo() {
        return demo;
    }

    public static class Security {
        private int bcryptStrength = 12;

        public int getBcryptStrength() {
            return bcryptStrength;
        }

        public void setBcryptStrength(int bcryptStrength) {
            this.bcryptStrength = bcryptStrength;
        }
    }

    public static class LoginRateLimit {
        private int maxAttempts = 5;
        private int windowMinutes = 15;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public int getWindowMinutes() {
            return windowMinutes;
        }

        public void setWindowMinutes(int windowMinutes) {
            this.windowMinutes = windowMinutes;
        }
    }

    public static class Bootstrap {
        private final Admin admin = new Admin();

        public Admin getAdmin() {
            return admin;
        }
    }

    public static class Admin {
        private boolean enabled;
        private String name;
        private String email;
        private String password;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    /**
     * The demo dataset: the curated course catalogue and the fictional
     * sample identities of a demonstration environment.
     *
     * <p>It is off unless it is switched on explicitly, and it refuses
     * to run unless the operator also names the database it may write
     * to. The production profile pins {@code enabled} to false, so the
     * demo data can never be seeded into a production database by
     * accident.</p>
     */
    public static class Demo {
        private boolean enabled;
        private String targetDatabase = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** The name of the one database the seeder may write to. */
        public String getTargetDatabase() {
            return targetDatabase;
        }

        public void setTargetDatabase(String targetDatabase) {
            this.targetDatabase = targetDatabase;
        }
    }
}
