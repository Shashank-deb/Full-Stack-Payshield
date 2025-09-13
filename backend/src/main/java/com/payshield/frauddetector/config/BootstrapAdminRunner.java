// ==============================================================================
// COMPLETE: BootstrapAdminRunner.java - Enhanced with Error Handling & Validation
// File: backend/src/main/java/com/payshield/frauddetector/config/BootstrapAdminRunner.java
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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Configuration
public class BootstrapAdminRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    // Email validation pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    // Password strength requirements
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 100;

    @Bean
    ApplicationRunner seedFirstAdmin(
            SpringUserRepository users,
            PasswordEncoder encoder,
            JdbcTemplate jdbcTemplate,
            @Value("${bootstrap.admin.email:}") String adminEmail,
            @Value("${bootstrap.admin.password:}") String adminPassword,
            @Value("${bootstrap.defaultTenantId:}") String defaultTenantId
    ) {
        return args -> {
            log.info("🚀 Starting PayShield bootstrap process...");

            try {
                // ===================================================================
                // 1. VALIDATE INPUT PARAMETERS
                // ===================================================================
                if (!validateBootstrapParameters(adminEmail, adminPassword)) {
                    log.warn("❌ Bootstrap admin creation skipped - invalid parameters");
                    logBootstrapInstructions();
                    return;
                }

                // ===================================================================
                // 2. DETERMINE TENANT ID
                // ===================================================================
                UUID tenantId = resolveTenantId(defaultTenantId);
                log.info("📋 Using tenant ID: {}", tenantId);

                // ===================================================================
                // 3. ENSURE TENANT EXISTS (CRITICAL FIX)
                // ===================================================================
                if (!ensureTenantExists(jdbcTemplate, tenantId)) {
                    log.error("❌ Failed to create/verify tenant - aborting bootstrap");
                    return;
                }

                // ===================================================================
                // 4. CHECK IF ADMIN ALREADY EXISTS
                // ===================================================================
                if (users.existsByEmail(adminEmail.toLowerCase())) {
                    log.info("ℹ️ Bootstrap admin already exists: {}", adminEmail);

                    // Verify the existing admin is properly configured
                    verifyExistingAdmin(users, adminEmail, tenantId);
                    return;
                }

                // ===================================================================
                // 5. CREATE BOOTSTRAP ADMIN USER
                // ===================================================================
                createBootstrapAdmin(users, encoder, adminEmail, adminPassword, tenantId);

                log.info("🎉 Bootstrap process completed successfully!");

            } catch (Exception e) {
                log.error("💥 Bootstrap process failed with error: {}", e.getMessage(), e);
                throw new RuntimeException("Bootstrap initialization failed", e);
            }
        };
    }

    /**
     * Validate bootstrap parameters
     */
    private boolean validateBootstrapParameters(String adminEmail, String adminPassword) {
        if (adminEmail == null || adminEmail.isBlank()) {
            log.warn("Bootstrap admin email is not set");
            return false;
        }

        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("Bootstrap admin password is not set");
            return false;
        }

        // Validate email format
        if (!EMAIL_PATTERN.matcher(adminEmail).matches()) {
            log.error("Invalid email format: {}", adminEmail);
            return false;
        }

        // Validate password strength
        if (!isPasswordStrong(adminPassword)) {
            log.error("Password does not meet security requirements");
            return false;
        }

        log.info("✅ Bootstrap parameters validated successfully");
        return true;
    }

    /**
     * Resolve tenant ID from configuration
     */
    private UUID resolveTenantId(String defaultTenantId) {
        try {
            if (defaultTenantId != null && !defaultTenantId.isBlank()) {
                UUID tenantId = UUID.fromString(defaultTenantId);
                log.info("Using configured tenant ID: {}", tenantId);
                return tenantId;
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid tenant ID format: {}, generating new UUID", defaultTenantId);
        }

        // Fallback to default tenant ID
        UUID fallbackTenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        log.info("Using default tenant ID: {}", fallbackTenantId);
        return fallbackTenantId;
    }

    /**
     * Ensure tenant record exists in database
     */
    @Transactional
    private boolean ensureTenantExists(JdbcTemplate jdbcTemplate, UUID tenantId) {
        try {
            log.info("🔍 Checking if tenant exists: {}", tenantId);

            // Check if tenant exists
            Long tenantCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tenant WHERE id = ?::uuid",
                    Long.class,
                    tenantId.toString()
            );

            if (tenantCount == null || tenantCount == 0) {
                log.info("🆕 Creating new tenant record...");

                // Create tenant record
                int rowsAffected = jdbcTemplate.update(
                        "INSERT INTO tenant (id, name, created_at) VALUES (?::uuid, ?, ?)",
                        tenantId.toString(),
                        "Default Tenant",
                        OffsetDateTime.now()
                );

                if (rowsAffected > 0) {
                    log.info("✅ Successfully created tenant: {} (Default Tenant)", tenantId);
                    return true;
                } else {
                    log.error("❌ Failed to create tenant record - no rows affected");
                    return false;
                }
            } else {
                log.info("✅ Tenant already exists: {}", tenantId);
                return true;
            }

        } catch (DataAccessException e) {
            log.error("❌ Database error while creating/checking tenant: {}", e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("❌ Unexpected error while ensuring tenant exists: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Create bootstrap admin user with enhanced security
     */
    @Transactional
    private void createBootstrapAdmin(SpringUserRepository users, PasswordEncoder encoder,
                                      String adminEmail, String adminPassword, UUID tenantId) {
        try {
            log.info("👤 Creating bootstrap admin user: {}", adminEmail);

            UserEntity adminUser = new UserEntity();
            adminUser.setId(UUID.randomUUID());
            adminUser.setEmail(adminEmail.toLowerCase());
            adminUser.setPasswordHash(encoder.encode(adminPassword));
            adminUser.setTenantId(tenantId);
            adminUser.setRoles(Set.of("ADMIN", "ANALYST", "APPROVER"));

            // ✅ FIXED: Set MFA fields properly (from V6 migration)
            adminUser.setMfaEnabled(false); // Start with MFA disabled for initial setup
            adminUser.setMfaEnforced(false); // Can be enabled later via admin panel
            adminUser.setMfaBackupCodesCount(0);

            // Save the user
            UserEntity savedUser = users.save(adminUser);

            if (savedUser != null && savedUser.getId() != null) {
                log.info("✅ Bootstrap admin created successfully!");
                log.info("📧 Email: {}", adminEmail);
                log.info("🏢 Tenant: {}", tenantId);
                log.info("🔑 Roles: {}", savedUser.getRoles());
                log.info("🔒 MFA Status: {}", savedUser.getMfaEnabled() ? "Enabled" : "Disabled");

                // Log security notice
                logSecurityNotice(adminPassword);

            } else {
                throw new RuntimeException("User creation failed - saved user is null");
            }

        } catch (Exception e) {
            log.error("❌ Failed to create bootstrap admin: {}", e.getMessage(), e);
            throw new RuntimeException("Bootstrap admin creation failed", e);
        }
    }

    /**
     * Verify existing admin configuration
     */
    private void verifyExistingAdmin(SpringUserRepository users, String adminEmail, UUID tenantId) {
        try {
            UserEntity existingAdmin = users.findByEmail(adminEmail.toLowerCase()).orElse(null);

            if (existingAdmin != null) {
                log.info("📋 Existing admin verification:");
                log.info("  - Email: {}", existingAdmin.getEmail());
                log.info("  - Tenant: {}", existingAdmin.getTenantId());
                log.info("  - Roles: {}", existingAdmin.getRoles());
                log.info("  - MFA Enabled: {}", existingAdmin.getMfaEnabled());
                log.info("  - Created: {}", existingAdmin.getCreatedAt());

                // Verify tenant matches
                if (!tenantId.equals(existingAdmin.getTenantId())) {
                    log.warn("⚠️ Tenant ID mismatch - Expected: {}, Actual: {}",
                            tenantId, existingAdmin.getTenantId());
                }

                // Verify admin has proper roles
                if (!existingAdmin.getRoles().contains("ADMIN")) {
                    log.warn("⚠️ Existing user does not have ADMIN role: {}", existingAdmin.getRoles());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to verify existing admin: {}", e.getMessage());
        }
    }

    /**
     * Check password strength requirements
     */
    private boolean isPasswordStrong(String password) {
        if (password.length() < MIN_PASSWORD_LENGTH) {
            log.error("Password too short - minimum {} characters required", MIN_PASSWORD_LENGTH);
            return false;
        }

        if (password.length() > MAX_PASSWORD_LENGTH) {
            log.error("Password too long - maximum {} characters allowed", MAX_PASSWORD_LENGTH);
            return false;
        }

        // Check for at least one digit, one letter
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);

        if (!hasDigit || !hasLetter) {
            log.error("Password must contain at least one letter and one digit");
            return false;
        }

        return true;
    }

    /**
     * Log bootstrap configuration instructions
     */
    private void logBootstrapInstructions() {
        log.info("");
        log.info("📋 BOOTSTRAP CONFIGURATION REQUIRED:");
        log.info("   Set the following environment variables:");
        log.info("   - BOOTSTRAP_ADMIN_EMAIL=admin@yourcompany.com");
        log.info("   - BOOTSTRAP_ADMIN_PASSWORD=<secure-password>");
        log.info("   - BOOTSTRAP_DEFAULT_TENANT_ID=<tenant-uuid> (optional)");
        log.info("");
        log.info("   Or add to application.yml:");
        log.info("   bootstrap:");
        log.info("     admin:");
        log.info("       email: admin@yourcompany.com");
        log.info("       password: <secure-password>");
        log.info("     defaultTenantId: <tenant-uuid>");
        log.info("");
    }

    /**
     * Log important security notice
     */
    private void logSecurityNotice(String password) {
        log.info("");
        log.info("🔒 SECURITY NOTICE:");
        log.info("   1. Change the default password immediately after first login");
        log.info("   2. Enable MFA for the admin account");
        log.info("   3. Remove bootstrap credentials from environment/config");
        log.info("   4. Review and configure additional security settings");

        // Only show password in development
        if (isDevEnvironment()) {
            log.info("   5. Current password: {} (DEVELOPMENT ONLY)", password);
        }

        log.info("");
    }

    /**
     * Check if running in development environment
     */
    private boolean isDevEnvironment() {
        String profile = System.getProperty("spring.profiles.active", "");
        String env = System.getenv("SPRING_PROFILES_ACTIVE");

        return "local".equals(profile) || "dev".equals(profile) ||
                "local".equals(env) || "dev".equals(env) ||
                "development".equals(env);
    }
}