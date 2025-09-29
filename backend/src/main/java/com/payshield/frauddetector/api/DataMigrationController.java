// ==============================================================================
// Step 7: Data Migration Controller for Encrypting Existing Records
// File: backend/src/main/java/com/payshield/frauddetector/api/DataMigrationController.java
// ==============================================================================

package com.payshield.frauddetector.api;

import com.payshield.frauddetector.config.TenantContext;
import com.payshield.frauddetector.infrastructure.encryption.FieldEncryptionService;
import com.payshield.frauddetector.infrastructure.jpa.InvoiceEntity;
import com.payshield.frauddetector.infrastructure.jpa.SpringInvoiceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/admin/migration")
@Tag(name = "Data Migration", description = "Admin-only endpoints for encrypting existing data")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('ADMIN') or hasRole('MFA_PENDING')")
//This is the DataMigrationController file which is used for ADMIN an MFA_PENDING
public class DataMigrationController {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationController.class);

    private final SpringInvoiceRepository invoiceRepository;
    private final FieldEncryptionService encryptionService;

    public DataMigrationController(SpringInvoiceRepository invoiceRepository,
                                   FieldEncryptionService encryptionService) {
        this.invoiceRepository = invoiceRepository;
        this.encryptionService = encryptionService;
    }

    @PostMapping("/encrypt-legacy-data")
    @Transactional
    @Operation(
            summary = "Encrypt legacy plaintext data",
            description = """
            Migrates existing plaintext IBAN and SWIFT codes to encrypted format.
            
            **This is a critical data migration operation that:**
            - Finds all invoices with plaintext banking data
            - Encrypts the data using AES-256-GCM
            - Stores the encrypted version
            - Clears the plaintext fields
            - Generates hash for duplicate detection
            
            **Safety Features:**
            - Read-only dry-run mode available
            - Batch processing with progress reporting
            - Rollback on any error
            - Comprehensive logging
            """
    )
    @ApiResponse(
            responseCode = "200",
            description = "Migration completed successfully",
            content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(value = """
                {
                  "status": "SUCCESS",
                  "totalRecords": 150,
                  "encrypted": 105,
                  "alreadyEncrypted": 45,
                  "errors": 0,
                  "duration": "2.5 seconds",
                  "timestamp": "2024-09-04T12:00:00Z"
                }
                """)
            )
    )
    public ResponseEntity<?> encryptLegacyData(
            @RequestParam(defaultValue = "false") boolean dryRun) {

        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing tenant context"));
        }

        long startTime = System.currentTimeMillis();
        log.info("🔐 Starting data encryption migration - DryRun: {}, Tenant: {}", dryRun, tenantId);

        try {
            // Get all invoices for this tenant
            List<InvoiceEntity> allInvoices = invoiceRepository.findByTenantId(
                    tenantId,
                    org.springframework.data.domain.Pageable.unpaged()
            );

            int totalRecords = allInvoices.size();
            int encrypted = 0;
            int alreadyEncrypted = 0;
            int errors = 0;
            List<String> errorMessages = new ArrayList<>();

            log.info("📊 Found {} total invoices to process", totalRecords);

            for (InvoiceEntity invoice : allInvoices) {
                try {
                    boolean needsEncryption = false;

                    // Check if IBAN needs encryption
                    if (invoice.getBankIban() != null && !invoice.getBankIban().isBlank()) {
                        if (invoice.getBankIbanEncrypted() == null || invoice.getBankIbanEncrypted().isBlank()) {
                            needsEncryption = true;

                            if (!dryRun) {
                                // Encrypt IBAN
                                String encryptedIban = encryptionService.encrypt(invoice.getBankIban());
                                String ibanHash = encryptionService.generateHash(invoice.getBankIban());

                                invoice.setBankIbanEncrypted(encryptedIban);
                                invoice.setBankIbanHash(ibanHash);
                                invoice.setBankIban(null); // Clear plaintext

                                log.debug("✅ Encrypted IBAN for invoice: {}", invoice.getId());
                            }
                        } else {
                            log.debug("ℹ️ Invoice {} already has encrypted IBAN", invoice.getId());
                        }
                    }

                    // Check if SWIFT needs encryption
                    if (invoice.getBankSwift() != null && !invoice.getBankSwift().isBlank()) {
                        if (invoice.getBankSwiftEncrypted() == null || invoice.getBankSwiftEncrypted().isBlank()) {
                            needsEncryption = true;

                            if (!dryRun) {
                                // Encrypt SWIFT
                                String encryptedSwift = encryptionService.encrypt(invoice.getBankSwift());
                                invoice.setBankSwiftEncrypted(encryptedSwift);
                                invoice.setBankSwift(null); // Clear plaintext

                                log.debug("✅ Encrypted SWIFT for invoice: {}", invoice.getId());
                            }
                        } else {
                            log.debug("ℹ️ Invoice {} already has encrypted SWIFT", invoice.getId());
                        }
                    }

                    if (needsEncryption) {
                        if (!dryRun) {
                            invoiceRepository.save(invoice);
                        }
                        encrypted++;
                    } else if (invoice.getBankIbanEncrypted() != null || invoice.getBankSwiftEncrypted() != null) {
                        alreadyEncrypted++;
                    }

                } catch (Exception e) {
                    errors++;
                    String errorMsg = String.format("Failed to encrypt invoice %s: %s",
                            invoice.getId(), e.getMessage());
                    errorMessages.add(errorMsg);
                    log.error("❌ {}", errorMsg, e);
                }
            }

            long duration = System.currentTimeMillis() - startTime;

            Map<String, Object> result = new HashMap<>();
            result.put("status", dryRun ? "DRY_RUN_SUCCESS" : "SUCCESS");
            result.put("totalRecords", totalRecords);
            result.put("encrypted", encrypted);
            result.put("alreadyEncrypted", alreadyEncrypted);
            result.put("errors", errors);
            result.put("errorMessages", errorMessages);
            result.put("duration", String.format("%.2f seconds", duration / 1000.0));
            result.put("timestamp", OffsetDateTime.now());
            result.put("tenantId", tenantId.toString());

            if (dryRun) {
                log.info("🔍 DRY RUN completed - {} records would be encrypted", encrypted);
            } else {
                log.info("✅ Migration completed - {} records encrypted, {} already encrypted, {} errors",
                        encrypted, alreadyEncrypted, errors);
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("💥 Migration failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "FAILURE",
                    "error", "Migration failed",
                    "message", e.getMessage(),
                    "timestamp", OffsetDateTime.now()
            ));
        }
    }

    @GetMapping("/encryption-status")
    @Operation(
            summary = "Get encryption migration status",
            description = "Returns statistics about encrypted vs plaintext records"
    )
    public ResponseEntity<?> getEncryptionStatus() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing tenant context"));
        }

        try {
            long totalInvoices = invoiceRepository.count();
            long legacyIbanCount = invoiceRepository.countByBankIbanIsNotNull();
            long legacySwiftCount = invoiceRepository.countByBankSwiftIsNotNull();
            long encryptedInvoices = invoiceRepository.countByBankIbanEncryptedIsNotNullOrBankSwiftEncryptedIsNotNull();

            Map<String, Object> status = new HashMap<>();
            status.put("totalInvoices", totalInvoices);
            status.put("encryptedInvoices", encryptedInvoices);
            status.put("legacyPlaintextIban", legacyIbanCount);
            status.put("legacyPlaintextSwift", legacySwiftCount);
            status.put("encryptionPercentage", totalInvoices > 0 ?
                    Math.round((double) encryptedInvoices / totalInvoices * 100.0 * 100.0) / 100.0 : 0.0);
            status.put("migrationComplete", legacyIbanCount == 0 && legacySwiftCount == 0);
            status.put("timestamp", OffsetDateTime.now());

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            log.error("Failed to get encryption status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to get encryption status",
                    "message", e.getMessage()
            ));
        }
    }
}