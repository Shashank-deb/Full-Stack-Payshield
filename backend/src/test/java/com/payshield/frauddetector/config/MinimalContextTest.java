package com.payshield.frauddetector.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payshield.frauddetector.infrastructure.encryption.FieldEncryptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {
        FieldEncryptionService.class
}, properties = {
        "app.encryption.key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "app.encryption.key-version=1"
})
@ActiveProfiles("test")
@Import(MinimalContextTest.TestConfig.class)
class MinimalContextTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private FieldEncryptionService encryptionService;

    @Test
    void contextLoads() {
        assertThat(encryptionService).isNotNull();
    }

    @Test
    void encryptionServiceWorks() {
        String testData = "test-encryption-data";

        // Test encryption
        String encrypted = encryptionService.encrypt(testData);
        assertThat(encrypted).isNotNull();
        assertThat(encrypted).isNotEqualTo(testData);

        // Test decryption
        String decrypted = encryptionService.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(testData);

        // Test hash generation
        String hash = encryptionService.generateHash(testData);
        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64); // SHA-256 hex length
    }

    @Test
    void encryptionServiceHandlesNullValues() {
        assertThat(encryptionService.encrypt(null)).isNull();
        assertThat(encryptionService.encrypt("")).isNull();
        assertThat(encryptionService.decrypt(null)).isNull();
        assertThat(encryptionService.decrypt("")).isNull();
        assertThat(encryptionService.generateHash(null)).isNull();
        assertThat(encryptionService.generateHash("")).isNull();
    }

    // Encryption detection test removed as it's not part of the FieldEncryptionService interface
}