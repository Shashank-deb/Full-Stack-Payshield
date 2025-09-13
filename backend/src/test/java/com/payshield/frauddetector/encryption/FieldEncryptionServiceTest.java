// ==============================================================================
// Unit Test Example for FieldEncryptionService
// Create: backend/src/test/java/com/payshield/frauddetector/infrastructure/encryption/FieldEncryptionServiceTest.java
// ==============================================================================

package com.payshield.frauddetector.encryption;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payshield.frauddetector.infrastructure.encryption.FieldEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class FieldEncryptionServiceTest {

    private FieldEncryptionService encryptionService;
    private static final String TEST_KEY = "NywLhbIA9UvNfurxHK6JkZKYP7g6M4k1qGPAXMMppiQ=";

    @BeforeEach
    void setUp() {
        encryptionService = new FieldEncryptionService(TEST_KEY, 1, new ObjectMapper());
    }

    @Test
    void shouldEncryptAndDecryptSuccessfully() {
        // Given
        String originalData = "Sensitive bank account information";

        // When
        String encrypted = encryptionService.encrypt(originalData);
        String decrypted = encryptionService.decrypt(encrypted);

        // Then
        assertThat(encrypted).isNotNull();
        assertThat(encrypted).isNotEqualTo(originalData);
        assertThat(decrypted).isEqualTo(originalData);
    }

    @Test
    void shouldGenerateConsistentHashes() {
        // Given
        String data = "test-data";

        // When
        String hash1 = encryptionService.generateHash(data);
        String hash2 = encryptionService.generateHash(data);

        // Then
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // SHA-256 hex length
    }

    @Test
    void shouldHandleMultipleFieldsEncryption() {
        // Given
        Map<String, String> originalFields = Map.of(
            "iban", "GB29NWBK60161331926819",
            "swift", "NWBKGB2L",
            "accountName", "Test Account Holder"
        );

        // When
        Map<String, String> encrypted = encryptionService.encryptFields(originalFields);
        Map<String, String> decrypted = encryptionService.decryptFields(encrypted);

        // Then
        assertThat(encrypted).hasSize(3);
        assertThat(encrypted.values()).doesNotContainAnyElementsOf(originalFields.values());
        assertThat(decrypted).isEqualTo(originalFields);
    }

    @Test
    void shouldDetectEncryptedData() {
        // Given
        String plaintext = "plain text data";
        String encrypted = encryptionService.encrypt(plaintext);

        // When/Then
        assertThat(encryptionService.isEncrypted(plaintext)).isFalse();
        assertThat(encryptionService.isEncrypted(encrypted)).isTrue();
        assertThat(encryptionService.isEncrypted("random string")).isFalse();
    }

    @Test
    void shouldHandleNullAndEmptyInputs() {
        // Test null inputs
        assertThat(encryptionService.encrypt(null)).isNull();
        assertThat(encryptionService.decrypt(null)).isNull();
        assertThat(encryptionService.generateHash(null)).isNull();

        // Test empty inputs
        assertThat(encryptionService.encrypt("")).isNull();
        assertThat(encryptionService.decrypt("")).isNull();
        assertThat(encryptionService.generateHash("")).isNull();

        // Test whitespace
        assertThat(encryptionService.encrypt("   ")).isNull();
    }

    @Test
    void shouldThrowExceptionForInvalidKey() {
        // Given/When/Then
        assertThatThrownBy(() -> new FieldEncryptionService("invalid-key", 1, new ObjectMapper()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Invalid encryption key configuration");
    }

    @Test
    void shouldHandleDecryptionOfInvalidData() {
        // When/Then
        assertThatThrownBy(() -> encryptionService.decrypt("invalid-encrypted-data"))
            .isInstanceOf(FieldEncryptionService.EncryptionException.class)
            .hasMessageContaining("Failed to decrypt field");
    }

    @Test
    void shouldReturnCorrectKeyVersion() {
        // When/Then
        assertThat(encryptionService.getCurrentKeyVersion()).isEqualTo(1);
    }

    @Test
    void shouldEncryptDifferentlyEachTime() {
        // Given
        String data = "same data";

        // When
        String encrypted1 = encryptionService.encrypt(data);
        String encrypted2 = encryptionService.encrypt(data);

        // Then
        assertThat(encrypted1).isNotEqualTo(encrypted2); // Due to random IV
        assertThat(encryptionService.decrypt(encrypted1)).isEqualTo(data);
        assertThat(encryptionService.decrypt(encrypted2)).isEqualTo(data);
    }
}