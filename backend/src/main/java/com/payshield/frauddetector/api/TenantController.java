// ==============================================================================
// NEW: TenantController.java - Tenant Management API
// File: src/main/java/com/payshield/frauddetector/api/TenantController.java
// ==============================================================================

package com.payshield.frauddetector.api;

import com.payshield.frauddetector.config.TenantContext;
import com.payshield.frauddetector.infrastructure.tenant.TenantValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/admin/tenants")
@Tag(name = "Tenant Management", description = "Admin-only endpoints for managing tenants")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('ADMIN')")
public class TenantController {

    private static final Logger log = LoggerFactory.getLogger(TenantController.class);

    private final TenantValidationService tenantService;

    public TenantController(TenantValidationService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    @Operation(
            summary = "List all tenants with statistics",
            description = "Retrieve all tenants with their associated entity counts (vendors, invoices, cases, users)"
    )
    @ApiResponse(
            responseCode = "200",
            description = "Tenants retrieved successfully",
            content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(value = """
            [
                {
                    "id": "00000000-0000-0000-0000-000000000001",
                    "name": "Default Tenant",
                    "vendorCount": 5,
                    "invoiceCount": 23,
                    "caseCount": 2,
                    "userCount": 3
                },
                {
                    "id": "11111111-1111-1111-1111-111111111111",
                    "name": "Acme Corporation",
                    "vendorCount": 12,
                    "invoiceCount": 89,
                    "caseCount": 7,
                    "userCount": 8
                }
            ]
            """)
            )
    )
    public ResponseEntity<?> listTenants() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        log.info("Admin tenant list requested by: {}", auth != null ? auth.getName() : "unknown");

        try {
            List<TenantValidationService.TenantStats> tenants = tenantService.getAllTenantStats();

            log.info("Retrieved {} tenants for admin view", tenants.size());
            return ResponseEntity.ok(tenants);

        } catch (Exception e) {
            log.error("Failed to retrieve tenant list: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Failed to retrieve tenants", "message", e.getMessage())
            );
        }
    }

    @GetMapping("/{tenantId}")
    @Operation(
            summary = "Get tenant by ID",
            description = "Retrieve detailed information about a specific tenant"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tenant found",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                {
                    "id": "00000000-0000-0000-0000-000000000001",
                    "name": "Default Tenant"
                }
                """)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Tenant not found",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                {
                    "error": "Tenant not found",
                    "tenantId": "00000000-0000-0000-0000-000000000002"
                }
                """)
                    )
            )
    })
    public ResponseEntity<?> getTenant(
            @Parameter(description = "Tenant UUID", example = "00000000-0000-0000-0000-000000000001")
            @PathVariable UUID tenantId
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        log.info("Admin tenant lookup requested by: {} for tenant: {}",
                auth != null ? auth.getName() : "unknown", tenantId);

        try {
            Optional<TenantValidationService.TenantInfo> tenant = tenantService.getTenantInfo(tenantId);

            if (tenant.isPresent()) {
                return ResponseEntity.ok(tenant.get());
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        Map.of("error", "Tenant not found", "tenantId", tenantId.toString())
                );
            }

        } catch (Exception e) {
            log.error("Failed to retrieve tenant {}: {}", tenantId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Failed to retrieve tenant", "message", e.getMessage())
            );
        }
    }

    @PostMapping
    @Operation(
            summary = "Create a new tenant",
            description = "Create a new tenant with the specified name. Tenant names must be unique."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Tenant created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                {
                    "id": "22222222-2222-2222-2222-222222222222",
                    "name": "New Company Ltd",
                    "createdAt": "2024-09-21T10:30:00Z",
                    "createdBy": "admin@payshield.com"
                }
                """)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request or tenant name already exists",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                {
                    "error": "Tenant name already exists",
                    "name": "Existing Company"
                }
                """)
                    )
            )
    })
    public ResponseEntity<?> createTenant(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Tenant creation request",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                {
                    "name": "New Company Ltd"
                }
                """)
                    )
            )
            @RequestBody @Valid CreateTenantRequest request
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        log.info("Admin tenant creation requested by: {} for name: '{}'",
                auth != null ? auth.getName() : "unknown", request.name());

        try {
            // Validate tenant name uniqueness
            if (!tenantService.isTenantNameUnique(request.name())) {
                log.warn("Tenant creation failed - name already exists: '{}'", request.name());
                return ResponseEntity.badRequest().body(
                        Map.of("error", "Tenant name already exists", "name", request.name())
                );
            }

            // Create the tenant
            TenantValidationService.TenantInfo newTenant = tenantService.createTenant(request.name());

            Map<String, Object> response = Map.of(
                    "id", newTenant.id().toString(),
                    "name", newTenant.name(),
                    "createdAt", OffsetDateTime.now().toString(),
                    "createdBy", auth != null ? auth.getName() : "system"
            );

            log.info("✅ Tenant created successfully: {} ({})", newTenant.id(), newTenant.name());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid tenant creation request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Invalid request", "message", e.getMessage())
            );
        } catch (Exception e) {
            log.error("Failed to create tenant '{}': {}", request.name(), e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Failed to create tenant", "message", e.getMessage())
            );
        }
    }

    @GetMapping("/current")
    @Operation(
            summary = "Get current user's tenant",
            description = "Retrieve information about the current user's tenant based on JWT context"
    )
    @ApiResponse(
            responseCode = "200",
            description = "Current tenant information",
            content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(value = """
            {
                "id": "00000000-0000-0000-0000-000000000001",
                "name": "Default Tenant",
                "isValid": true
            }
            """)
            )
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'APPROVER')")
    public ResponseEntity<?> getCurrentTenant() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID currentTenantId = TenantContext.getTenantId();

        log.info("Current tenant lookup by: {} (tenant: {})",
                auth != null ? auth.getName() : "unknown", currentTenantId);

        if (currentTenantId == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "No tenant context available")
            );
        }

        try {
            Optional<TenantValidationService.TenantInfo> tenant = tenantService.getTenantInfo(currentTenantId);

            if (tenant.isPresent()) {
                Map<String, Object> response = Map.of(
                        "id", tenant.get().id().toString(),
                        "name", tenant.get().name(),
                        "isValid", true
                );
                return ResponseEntity.ok(response);
            } else {
                log.warn("Current tenant not found in database: {}", currentTenantId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        Map.of(
                                "error", "Current tenant not found",
                                "tenantId", currentTenantId.toString(),
                                "isValid", false
                        )
                );
            }

        } catch (Exception e) {
            log.error("Failed to retrieve current tenant {}: {}", currentTenantId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Failed to retrieve current tenant", "message", e.getMessage())
            );
        }
    }

    @PostMapping("/validate")
    @Operation(
            summary = "Validate tenant access",
            description = "Validate that the current user has access to the specified tenant"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tenant access validation result",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                {
                    "tenantId": "00000000-0000-0000-0000-000000000001",
                    "hasAccess": true,
                    "currentTenant": "00000000-0000-0000-0000-000000000001",
                    "message": "Access granted"
                }
                """)
                    )
            )
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'APPROVER')")
    public ResponseEntity<?> validateTenantAccess(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Tenant validation request",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                {
                    "tenantId": "00000000-0000-0000-0000-000000000001"
                }
                """)
                    )
            )
            @RequestBody @Valid ValidateTenantRequest request
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID currentTenantId = TenantContext.getTenantId();

        log.info("Tenant access validation by: {} for tenant: {} (current: {})",
                auth != null ? auth.getName() : "unknown", request.tenantId(), currentTenantId);

        try {
            boolean hasAccess = currentTenantId != null && currentTenantId.equals(request.tenantId());
            boolean tenantExists = tenantService.tenantExists(request.tenantId());

            Map<String, Object> response = Map.of(
                    "tenantId", request.tenantId().toString(),
                    "hasAccess", hasAccess,
                    "currentTenant", currentTenantId != null ? currentTenantId.toString() : null,
                    "tenantExists", tenantExists,
                    "message", hasAccess ? "Access granted" : "Access denied"
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to validate tenant access for {}: {}", request.tenantId(), e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Failed to validate tenant access", "message", e.getMessage())
            );
        }
    }

    // ==========================================
    // REQUEST DTOs
    // ==========================================

    @Schema(description = "Request to create a new tenant")
    public record CreateTenantRequest(
            @Schema(description = "Tenant name", example = "Acme Corporation")
            @NotBlank(message = "Tenant name is required")
            @Size(min = 2, max = 100, message = "Tenant name must be between 2 and 100 characters")
            String name
    ) {}
}

