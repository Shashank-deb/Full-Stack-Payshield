package com.payshield.frauddetector.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payshield.frauddetector.application.InvoiceDetectionService;
import com.payshield.frauddetector.domain.ports.FileStoragePort;
import com.payshield.frauddetector.domain.ports.NotifierPort;
import com.payshield.frauddetector.infrastructure.encryption.FieldEncryptionService;
import com.payshield.frauddetector.infrastructure.security.FileUploadSecurityService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@TestConfiguration
@Profile("test")
public class TestConfig {

    /** Mock FileStoragePort for tests */
    @Bean
    @Primary
    public FileStoragePort testFileStoragePort() {
        return new FileStoragePort() {
            @Override
            public Path store(UUID tenantId, String sha256, String originalFilename, InputStream body) {
                try {
                    Path tempDir = Files.createTempDirectory("payshield-test-" + tenantId);
                    Path storedFile = tempDir.resolve(originalFilename == null ? "upload.bin" : originalFilename);
                    Files.copy(body, storedFile, StandardCopyOption.REPLACE_EXISTING);
                    return storedFile;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to store test file", e);
                }
            }
        };
    }

    /** Mock NotifierPort for tests */
    @Bean
    @Primary
    public NotifierPort testNotifierPort() {
        return new NotifierPort() {
            @Override
            public void sendCaseFlagged(UUID tenantId, UUID caseId, Map<String, Object> payload) {
                // No-op for tests
                System.out.println("TEST NOTIFICATION: Case " + caseId + " flagged for tenant " + tenantId);
            }
        };
    }

    /** Mock PdfParser for tests */
    @Bean
    @Primary
    public InvoiceDetectionService.PdfParser testPdfParser() {
        return new InvoiceDetectionService.PdfParser() {
            @Override
            public InvoiceDetectionService.Parsed parse(Path storedPath) {
                InvoiceDetectionService.Parsed parsed = new InvoiceDetectionService.Parsed();
                parsed.vendorName = "Test Vendor Corp";
                parsed.amount = new BigDecimal("1500.00");
                parsed.currency = "USD";
                parsed.bankIban = "GB29NWBK60161331926819";
                parsed.bankSwift = "NWBKGB2L";
                parsed.bankLast4 = "6819";
                return parsed;
            }
        };
    }

    /** Mock OutboxPort for tests */
    @Bean
    @Primary
    public InvoiceDetectionService.OutboxPort testOutboxPort() {
        return new InvoiceDetectionService.OutboxPort() {
            @Override
            public void publish(UUID tenantId, String type, String jsonPayload) {
                // No-op for tests
                System.out.println("TEST OUTBOX: " + type + " for tenant " + tenantId);
            }
        };
    }

    /** Mock FileUploadSecurityService for tests */
    @Bean
    @Primary
    public FileUploadSecurityService testFileUploadSecurityService() {
        return new FileUploadSecurityService(
                false,      // clamAvEnabled
                "localhost",// clamAvHost
                3310,       // clamAvPort
                30000,      // clamAvTimeout
                false,      // failSecure
                false,      // validateContent (disabled in tests)
                false       // strictMimeCheck (disabled in tests)
        );
    }

    /** Test ObjectMapper */
    @Bean
    @Primary
    public ObjectMapper testObjectMapper() {
        return new ObjectMapper();
    }

    /** Test FieldEncryptionService with proper key */
    @Bean
    @Primary
    public FieldEncryptionService testFieldEncryptionService(ObjectMapper objectMapper) {
        return new FieldEncryptionService(
                "NywLhbIA9UvNfurxHK6JkZKYP7g6M4k1qGPAXMMppiQ=", // app.encryption.key (tests only)
                1, // key version
                objectMapper
        );
    }
}
