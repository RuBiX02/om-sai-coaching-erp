package com.omsai.coaching.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA auditing so created_at / updated_at are maintained
 * automatically for every entity.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
