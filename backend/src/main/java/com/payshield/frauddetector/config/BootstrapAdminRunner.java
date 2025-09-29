// ==============================================================================
// FIXED: BootstrapAdminRunner.java - Robust Tenant and Admin Creation
// File: src/main/java/com/payshield/frauddetector/config/BootstrapAdminRunner.java
// ==============================================================================

package com.payshield.frauddetector.config;

import com.payshield.frauddetector.infrastructure.jpa.SpringUserRepository;
import com.payshield.frauddetector.infrastructure.jpa.UserEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Configuration
public class BootstrapAdminRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    @Bean
    ApplicationRunner seedFirstAdmin(
            SpringUserRepository users,
            PasswordEncoder encoder,
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            @Value("${bootstrap.admin.email:}") String adminEmail,
            @Value("${bootstrap.admin.password:}") String adminPassword,
            @Value("${bootstrap.defaultTenantId:}") String defaultTenantId
    ) {
        return args -> {
            log.info("🚀 Starting PayShield bootstrap process...");

            // Validate configuration
            if (!isValidConfiguration(adminEmail, adminPassword, defaultTenantId)) {
                log.warn("⚠️  Bootstrap skipped - missing or invalid configuration");
                log.info("Set these environment variables:");
                log.info("  BOOTSTRAP_ADMIN_EMAIL=admin@yourcompany.com");
                log.info("  BOOTSTRAP_ADMIN_PASSWORD=secure-password");
                log.info("  BOOTSTRAP_DEFAULT_TENANT_ID=00000000-0000-0000-0000-000000000001");
                return;
            }

            UUID tenantId = parseTenantId(defaultTenantId);
            if (tenantId == null) {
                log.error("❌ Invalid tenant ID format: {}", defaultTenantId);
                return;
            }

            // Execute bootstrap in transaction
            try {
                transactionTemplate.execute(status -> {
                    createTenantIfNotExists(jdbcTemplate, tenantId);
                    createAdminUserIfNotExists(users, encoder, tenantId, adminEmail, adminPassword);
                    return null;
                });

                log.info("✅ Bootstrap completed successfully!");
                log.info("🔐 Admin user: {} (tenant: {})", adminEmail, tenantId);

            } catch (Exception e) {
                log.error("❌ Bootstrap failed: {}", e.getMessage(), e);
                throw new RuntimeException("Bootstrap process failed", e);
            }
        };
    }

    /**
     * Validate all required configuration is present
     */
    private boolean isValidConfiguration(String adminEmail, String adminPassword, String defaultTenantId) {
        boolean isValid = true;

        if (adminEmail == null || adminEmail.isBlank()) {
            log.error("❌ Missing bootstrap.admin.email configuration");
            isValid = false;
        } else if (!isValidEmail(adminEmail)) {
            log.error("❌ Invalid email format: {}", adminEmail);
            isValid = false;
        }

        if (adminPassword == null || adminPassword.isBlank()) {
            log.error("❌ Missing bootstrap.admin.password configuration");
            isValid = false;
        } else if (adminPassword.length() < 8) {
            log.error("❌ Admin password must be at least 8 characters");
            isValid = false;
        }

        if (defaultTenantId == null || defaultTenantId.isBlank()) {
            log.error("❌ Missing bootstrap.defaultTenantId configuration");
            isValid = false;
        }

        return isValid;
    }

    /**
     * Parse and validate tenant ID format
     */
    private UUID parseTenantId(String tenantIdStr) {
        try {
            return UUID.fromString(tenantIdStr);
        } catch (IllegalArgumentException e) {
            log.error("❌ Invalid UUID format for tenant ID: {}", tenantIdStr);
            return null;
        }
    }

    /**
     * Create tenant if it doesn't exist
     */
    private void createTenantIfNotExists(JdbcTemplate jdbcTemplate, UUID tenantId) {
        try {
            // Check if tenant exists
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tenant WHERE id = ?",
                    Integer.class,
                    tenantId
            );

            if (count != null && count > 0) {
                log.info("✅ Tenant already exists: {}", tenantId);
                return;
            }

            // Create tenant
            String tenantName = "Default Tenant";
            int result = jdbcTemplate.update(
                    "INSERT INTO tenant (id, name) VALUES (?, ?)",
                    tenantId,
                    tenantName
            );

            if (result > 0) {
                log.info("✅ Created tenant: {} ({})", tenantId, tenantName);
            } else {
                throw new RuntimeException("Failed to create tenant - no rows affected");
            }

        } catch (DataIntegrityViolationException e) {
            // Tenant might exist due to race condition - check again
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tenant WHERE id = ?",
                    Integer.class,
                    tenantId
            );

            if (count != null && count > 0) {
                log.info("✅ Tenant exists (race condition handled): {}", tenantId);
            } else {
                log.error("❌ Failed to create tenant due to constraint violation: {}", e.getMessage());
                throw e;
            }
        } catch (Exception e) {
            log.error("❌ Unexpected error creating tenant {}: {}", tenantId, e.getMessage());
            throw new RuntimeException("Failed to create tenant", e);
        }
    }

    /**
     * Create admin user if it doesn't exist
     */
    private void createAdminUserIfNotExists(SpringUserRepository users, PasswordEncoder encoder,
                                            UUID tenantId, String adminEmail, String adminPassword) {
        try {
            String normalizedEmail = adminEmail.toLowerCase().trim();

            // Check if admin user already exists
            if (users.existsByEmail(normalizedEmail)) {
                log.info("✅ Admin user already exists: {}", normalizedEmail);

                // Verify the user is in the correct tenant
                users.findByEmail(normalizedEmail).ifPresent(user -> {
                    if (!tenantId.equals(user.getTenantId())) {
                        log.warn("⚠️  Admin user exists but in different tenant: {} vs {}",
                                user.getTenantId(), tenantId);
                    }
                });
                return;
            }

            // Create new admin user
            UserEntity adminUser = new UserEntity();
            adminUser.setId(UUID.randomUUID());
            adminUser.setEmail(normalizedEmail);
            adminUser.setPasswordHash(encoder.encode(adminPassword));
            adminUser.setTenantId(tenantId);
            adminUser.setRoles(Set.of("ADMIN", "ANALYST", "APPROVER"));
            adminUser.setCreatedAt(OffsetDateTime.now());

            // Set MFA as optional for bootstrap admin
            adminUser.setMfaEnabled(false);
            adminUser.setMfaEnforced(false);

            UserEntity savedUser = users.save(adminUser);

            log.info("✅ Created admin user: {} (ID: {})", normalizedEmail, savedUser.getId());
            log.info("🔑 Admin roles: {}", savedUser.getRoles());

            // Security reminder
            log.warn("🔐 SECURITY REMINDER: Change the default admin password after first login!");

        } catch (DataIntegrityViolationException e) {
            // User might exist due to race condition
            if (users.existsByEmail(adminEmail.toLowerCase())) {
                log.info("✅ Admin user exists (race condition handled): {}", adminEmail);
            } else {
                log.error("❌ Failed to create admin user due to constraint violation: {}", e.getMessage());
                throw e;
            }
        } catch (Exception e) {
            log.error("❌ Unexpected error creating admin user {}: {}", adminEmail, e.getMessage());
            throw new RuntimeException("Failed to create admin user", e);
        }
    }

    /**
     * Validate email format
     */
    private boolean isValidEmail(String email) {
        return email != null &&
                email.contains("@") &&
                email.length() > 5 &&
                email.length() <= 320 &&
                !email.startsWith("@") &&
                !email.endsWith("@");
    }
}