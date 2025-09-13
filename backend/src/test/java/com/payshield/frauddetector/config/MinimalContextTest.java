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
        "app.encryption.key=NywLhbIA9UvNfurxHK6JkZKYP7g6M4k1qGPAXMMppiQ=",
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

    @Autowired(required = false)
    private FieldEncryptionService encryptionService;

    @Test
    void contextLoads() {
        assertThat(encryptionService).isNotNull();
    }

    @Test
    void encryptionServiceWorks() {
        if (encryptionService != null) {
            String testData = "test-encryption-data";
            String encrypted = encryptionService.encrypt(testData);
            String decrypted = encryptionService.decrypt(encrypted);

            assertThat(encrypted).isNotEqualTo(testData);
            assertThat(decrypted).isEqualTo(testData);
        }
    }
}