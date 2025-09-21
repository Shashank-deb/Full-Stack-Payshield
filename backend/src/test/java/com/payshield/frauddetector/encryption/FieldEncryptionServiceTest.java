package com.payshield.frauddetector.encryption;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payshield.frauddetector.infrastructure.encryption.FieldEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldEncryptionServiceTest {

    private FieldEncryptionService svc;

    @BeforeEach
    void setUp() {
        // Same key as test application.properties
        String base64Key = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
        svc = new FieldEncryptionService(base64Key, 1, new ObjectMapper());
    }

    @Test
    void roundTrip() {
        String data = "top-secret";
        String enc = svc.encrypt(data);
        assertThat(enc).isNotBlank();
        String dec = svc.decrypt(enc);
        assertThat(dec).isEqualTo(data);
    }

    @Test
    void differentCiphertextForSamePlaintext() {
        String data = "same data";
        String e1 = svc.encrypt(data);
        String e2 = svc.encrypt(data);
        assertThat(e1).isNotEqualTo(e2);
        assertThat(svc.decrypt(e1)).isEqualTo(data);
        assertThat(svc.decrypt(e2)).isEqualTo(data);
    }

    @Test
    void hashIsStable() {
        String h1 = svc.generateHash("x");
        String h2 = svc.generateHash("x");
        assertThat(h1).isEqualTo(h2);
    }

    @Test
    void detectionWorks() {
        String enc = svc.encrypt("zzz");
        assertThat(svc.isEncrypted(enc)).isTrue();
        assertThat(svc.isEncrypted("zzz")).isFalse();
    }
}
