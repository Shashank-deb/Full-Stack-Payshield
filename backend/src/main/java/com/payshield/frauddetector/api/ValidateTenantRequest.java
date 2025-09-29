package com.payshield.frauddetector.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request to validate tenant access")
public record ValidateTenantRequest(
        @Schema(description = "Tenant UUID to validate", example = "00000000-0000-0000-0000-000000000001")
        @NotNull(message = "Tenant ID cannot be null")
        UUID tenantId
) {}
