// ==============================================================================
// Step 1.2: Batch Encryption Migration Tool with Progress Tracking
// File: backend/src/main/java/com/payshield/frauddetector/api/EncryptionMigrationController.java
// ==============================================================================

package com.payshield.frauddetector.api;

import com.payshield.frauddetector.config.TenantContext;
import com.payshield.frauddetector.infrastructure.encryption.FieldEncryptionService;
import com.payshield.frauddetector.infrastructure.jpa.InvoiceEntity;
import com.payshield.frauddetector.infrastructure.jpa.SpringInvoiceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/encryption")
@Tag(name = "Encryption Migration", description = "Admin tools for encrypting legacy data")
@PreAuthorize("hasRole('ADMIN')")
public class EncryptionMigrationController {

    private static final Logger log = LoggerFactory.getLogger(EncryptionMigrationController.class);
    private static final int BATCH_SIZE = 100;

    private final SpringInvoiceRepository invoiceRepository;
    private final FieldEncryptionService encryptionService;
    private final Map<UUID, MigrationStatus> activeMigrations = new ConcurrentHashMap<>();

    public EncryptionMigrationController(SpringInvoiceRepository invoiceRepository,
                                         FieldEncryptionService encryptionService) {
        this.invoiceRepository = invoiceRepository;
        this.encryptionService = encryptionService;
    }

    @PostMapping("/migrate/start")
    @Operation(summary = "Start async encryption migration",
            description = "Encrypts all legacy plaintext data in batches")
    public ResponseEntity<?> startMigration(@RequestParam(defaultValue = "false") boolean dryRun) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing tenant context"));
        }

        if (activeMigrations.containsKey(tenantId)) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Migration already in progress", "status", activeMigrations.get(tenantId)));
        }

        UUID migrationId = UUID.randomUUID();
        MigrationStatus status = new MigrationStatus(migrationId, tenantId, dryRun);
        activeMigrations.put(tenantId, status);

        // Start async migration
        performMigrationAsync(tenantId, status);

        return ResponseEntity.ok(Map.of(
                "migrationId", migrationId,
                "status", "STARTED",
                "dryRun", dryRun,
                "checkStatusUrl", "/admin/encryption/migrate/status"
        ));
    }

    @GetMapping("/migrate/status")
    @Operation(summary = "Check migration progress")
    public ResponseEntity<?> getMigrationStatus() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing tenant context"));
        }

        MigrationStatus status = activeMigrations.get(tenantId);
        if (status == null) {
            return ResponseEntity.ok(Map.of(
                    "status", "NO_ACTIVE_MIGRATION",
                    "lastCompleted", getLastCompletedMigration(tenantId)
            ));
        }

        return ResponseEntity.ok(status.toMap());
    }

    @Async
    protected void performMigrationAsync(UUID tenantId, MigrationStatus status) {
        try {
            log.info("🔐 Starting encryption migration for tenant: {} (dryRun={})",
                    tenantId, status.dryRun);

            // Get total count
            long totalCount = invoiceRepository.count();
            status.totalRecords = totalCount;

            // Process in batches
            int offset = 0;
            while (offset < totalCount && status.status == MigrationStatus.Status.RUNNING) {
                try {
                    int processed = processBatch(tenantId, offset, BATCH_SIZE, status);
                    offset += processed;

                    // Update progress
                    int progress = (int) ((offset * 100.0) / totalCount);
                    status.progressPercent = progress;

                    log.info("Migration progress: {}% ({}/{})", progress, offset, totalCount);

                    if (processed == 0) break; // No more records

                } catch (Exception e) {
                    log.error("Batch processing error at offset {}: {}", offset, e.getMessage(), e);
                    status.errors.add(new MigrationError(offset, e.getMessage()));
                    status.failedRecords.incrementAndGet();
                }
            }

            // Mark complete
            status.status = MigrationStatus.Status.COMPLETED;
            status.completedAt = OffsetDateTime.now();

            log.info("✅ Migration completed - Total: {}, Encrypted: {}, Failed: {}",
                    status.totalRecords, status.encryptedRecords, status.failedRecords);

        } catch (Exception e) {
            log.error("❌ Migration failed: {}", e.getMessage(), e);
            status.status = MigrationStatus.Status.FAILED;
            status.errors.add(new MigrationError(-1, "Migration failed: " + e.getMessage()));
        } finally {
            activeMigrations.remove(tenantId);
        }
    }

    @Transactional
    protected int processBatch(UUID tenantId, int offset, int batchSize, MigrationStatus status) {
        List<InvoiceEntity> batch = invoiceRepository.findByTenantId(
                tenantId,
                org.springframework.data.domain.PageRequest.of(offset / batchSize, batchSize)
        );

        for (InvoiceEntity invoice : batch) {
            try {
                boolean encrypted = false;

                // Encrypt IBAN
                if (invoice.getBankIban() != null && !invoice.getBankIban().isBlank() &&
                        (invoice.getBankIbanEncrypted() == null || invoice.getBankIbanEncrypted().isBlank())) {

                    if (!status.dryRun) {
                        String encryptedIban = encryptionService.encrypt(invoice.getBankIban());
                        String ibanHash = encryptionService.generateHash(invoice.getBankIban());
                        invoice.setBankIbanEncrypted(encryptedIban);
                        invoice.setBankIbanHash(ibanHash);
                        invoice.setBankIban(null); // Clear plaintext
                    }
                    encrypted = true;
                }

                // Encrypt SWIFT
                if (invoice.getBankSwift() != null && !invoice.getBankSwift().isBlank() &&
                        (invoice.getBankSwiftEncrypted() == null || invoice.getBankSwiftEncrypted().isBlank())) {

                    if (!status.dryRun) {
                        String encryptedSwift = encryptionService.encrypt(invoice.getBankSwift());
                        invoice.setBankSwiftEncrypted(encryptedSwift);
                        invoice.setBankSwift(null); // Clear plaintext
                    }
                    encrypted = true;
                }

                if (encrypted) {
                    if (!status.dryRun) {
                        invoiceRepository.save(invoice);
                    }
                    status.encryptedRecords.incrementAndGet();
                } else {
                    status.skippedRecords.incrementAndGet();
                }

            } catch (Exception e) {
                log.error("Failed to encrypt invoice {}: {}", invoice.getId(), e.getMessage());
                status.failedRecords.incrementAndGet();
                status.errors.add(new MigrationError(
                        offset + batch.indexOf(invoice),
                        "Invoice " + invoice.getId() + ": " + e.getMessage()
                ));
            }
        }

        return batch.size();
    }

    private Map<String, Object> getLastCompletedMigration(UUID tenantId) {
        // TODO: Store completed migrations in database
        return Map.of("available", false);
    }

    // DTO classes
    // DTO classes
    static class MigrationStatus {
        enum Status {STARTING, RUNNING, COMPLETED, FAILED}

        final UUID migrationId;
        final UUID tenantId;
        final boolean dryRun;
        final OffsetDateTime startedAt;

        Status status = Status.RUNNING;
        OffsetDateTime completedAt;
        long totalRecords = 0;
        AtomicInteger encryptedRecords = new AtomicInteger(0);
        AtomicInteger skippedRecords = new AtomicInteger(0);
        AtomicInteger failedRecords = new AtomicInteger(0);
        List<MigrationError> errors = Collections.synchronizedList(new ArrayList<>());
        volatile int progressPercent = 0;

        MigrationStatus(UUID migrationId, UUID tenantId, boolean dryRun) {
            this.migrationId = migrationId;
            this.tenantId = tenantId;
            this.dryRun = dryRun;
            this.startedAt = OffsetDateTime.now();
        }

        Map<String, Object> toMap() {
            return Map.of(
                    "migrationId", migrationId.toString(),
                    "status", status.toString(),
                    "dryRun", dryRun,
                    "progress", progressPercent + "%",
                    "totalRecords", totalRecords,
                    "encrypted", encryptedRecords.get(),
                    "skipped", skippedRecords.get(),
                    "failed", failedRecords.get(),
                    "startedAt", startedAt.toString(),
                    "completedAt", completedAt != null ? completedAt.toString() : null
            );
        }

        Map<String, Object> toMapWithErrors() {
            Map<String, Object> map = new HashMap<>(toMap());
            map.put("errors", errors.stream().limit(10).collect(Collectors.toList()));
            return map;
        }
    }

    public record MigrationError(int recordIndex, String message) {
    }

}