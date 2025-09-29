// ==============================================================================
// NEW: TenantValidationService.java - Tenant Validation and Management
// File: src/main/java/com/payshield/frauddetector/infrastructure/tenant/TenantValidationService.java
// ==============================================================================

package com.payshield.frauddetector.infrastructure.tenant;

import com.payshield.frauddetector.config.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class TenantValidationService {

    private static final Logger log = LoggerFactory.getLogger(TenantValidationService.class);

    private final JdbcTemplate jdbcTemplate;

    public TenantValidationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Validate that a tenant exists in the database
     */
    public boolean tenantExists(UUID tenantId) {
        if (tenantId == null) {
            log.debug("Tenant validation failed: null tenant ID");
            return false;
        }

        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tenant WHERE id = ?",
                    Integer.class,
                    tenantId
            );

            boolean exists = count != null && count > 0;
            log.debug("Tenant {} exists: {}", tenantId, exists);
            return exists;

        } catch (Exception e) {
            log.error("Error checking tenant existence for {}: {}", tenantId, e.getMessage());
            return false;
        }
    }

    /**
     * Validate the current tenant from context
     */
    public boolean validateCurrentTenant() {
        UUID currentTenantId = TenantContext.getTenantId();
        
        if (currentTenantId == null) {
            log.warn("No tenant ID in current context");
            return false;
        }

        return tenantExists(currentTenantId);
    }

    /**
     * Get tenant information by ID
     */
    public Optional<TenantInfo> getTenantInfo(UUID tenantId) {
        if (tenantId == null) {
            return Optional.empty();
        }

        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(
                    "SELECT id, name FROM tenant WHERE id = ?",
                    tenantId
            );

            return Optional.of(new TenantInfo(
                    (UUID) result.get("id"),
                    (String) result.get("name")
            ));

        } catch (EmptyResultDataAccessException e) {
            log.debug("Tenant not found: {}", tenantId);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error retrieving tenant info for {}: {}", tenantId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Create a new tenant
     */
    public TenantInfo createTenant(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tenant name cannot be null or empty");
        }

        UUID tenantId = UUID.randomUUID();
        String normalizedName = name.trim();

        try {
            int result = jdbcTemplate.update(
                    "INSERT INTO tenant (id, name) VALUES (?, ?)",
                    tenantId,
                    normalizedName
            );

            if (result > 0) {
                log.info("Created new tenant: {} ({})", tenantId, normalizedName);
                return new TenantInfo(tenantId, normalizedName);
            } else {
                throw new RuntimeException("Failed to create tenant - no rows affected");
            }

        } catch (Exception e) {
            log.error("Failed to create tenant '{}': {}", normalizedName, e.getMessage());
            throw new RuntimeException("Failed to create tenant", e);
        }
    }

    /**
     * List all tenants with basic statistics
     */
    public List<TenantStats> getAllTenantStats() {
        try {
            String sql = """
                SELECT 
                    t.id,
                    t.name,
                    COUNT(DISTINCT v.id) as vendor_count,
                    COUNT(DISTINCT i.id) as invoice_count,
                    COUNT(DISTINCT c.id) as case_count,
                    COUNT(DISTINCT u.id) as user_count
                FROM tenant t
                LEFT JOIN vendor v ON t.id = v.tenant_id
                LEFT JOIN invoice i ON t.id = i.tenant_id  
                LEFT JOIN case_workflow c ON t.id = c.tenant_id
                LEFT JOIN users u ON t.id = u.tenant_id
                GROUP BY t.id, t.name
                ORDER BY t.name
                """;

            return jdbcTemplate.query(sql, (rs, rowNum) -> new TenantStats(
                    rs.getObject("id", UUID.class),
                    rs.getString("name"),
                    rs.getLong("vendor_count"),
                    rs.getLong("invoice_count"),
                    rs.getLong("case_count"),
                    rs.getLong("user_count")
            ));

        } catch (Exception e) {
            log.error("Error retrieving tenant statistics: {}", e.getMessage());
            throw new RuntimeException("Failed to retrieve tenant statistics", e);
        }
    }

    /**
     * Validate tenant access for a specific entity
     */
    public void validateTenantAccess(UUID entityTenantId, String entityType, UUID entityId) {
        UUID currentTenantId = TenantContext.getTenantId();

        if (currentTenantId == null) {
            throw new TenantAccessException("No tenant context available");
        }

        if (!currentTenantId.equals(entityTenantId)) {
            log.warn("Tenant access violation - Current: {}, Entity: {} (type: {}, id: {})",
                    currentTenantId, entityTenantId, entityType, entityId);
            throw new TenantAccessException(
                    String.format("Access denied: %s %s belongs to different tenant", entityType, entityId));
        }
    }

    /**
     * Check if tenant name is unique
     */
    public boolean isTenantNameUnique(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }

        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tenant WHERE LOWER(name) = LOWER(?)",
                    Integer.class,
                    name.trim()
            );

            return count == null || count == 0;

        } catch (Exception e) {
            log.error("Error checking tenant name uniqueness for '{}': {}", name, e.getMessage());
            return false;
        }
    }

    /**
     * Get tenant by name
     */
    public Optional<TenantInfo> getTenantByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(
                    "SELECT id, name FROM tenant WHERE LOWER(name) = LOWER(?)",
                    name.trim()
            );

            return Optional.of(new TenantInfo(
                    (UUID) result.get("id"),
                    (String) result.get("name")
            ));

        } catch (EmptyResultDataAccessException e) {
            log.debug("Tenant not found by name: {}", name);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error retrieving tenant by name '{}': {}", name, e.getMessage());
            return Optional.empty();
        }
    }

    // ==========================================
    // DATA TRANSFER OBJECTS
    // ==========================================

    public record TenantInfo(UUID id, String name) {}

    public record TenantStats(
            UUID id,
            String name,
            long vendorCount,
            long invoiceCount,
            long caseCount,
            long userCount
    ) {}

    // ==========================================
    // CUSTOM EXCEPTIONS
    // ==========================================

    public static class TenantAccessException extends RuntimeException {
        public TenantAccessException(String message) {
            super(message);
        }

        public TenantAccessException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class TenantNotFoundException extends RuntimeException {
        public TenantNotFoundException(String message) {
            super(message);
        }

        public TenantNotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}