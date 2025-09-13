// ==============================================================================
// FIXED: JwtSecurityTest.java - Correct test configuration
// ==============================================================================

package com.payshield.frauddetector.security;

import com.payshield.frauddetector.config.JwtService;
import com.payshield.frauddetector.config.TestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {JwtService.class}, properties = {
        "security.jwt.secret=test-jwt-secret-key-that-is-long-enough-for-hmac-sha256-algorithm-tests-12345",
        "security.jwt.ttl-seconds=3600"
})
@ActiveProfiles("test")
@Import(TestConfig.class)
class JwtSecurityTest {

    @Autowired
    private JwtService jwtService;

    @Test
    void shouldGenerateValidToken() {
        UUID tenantId = UUID.randomUUID();
        String token = jwtService.generateToken("test@example.com", tenantId, Set.of("ADMIN"));

        assertThat(token).isNotEmpty();
        assertThat(jwtService.getSubject(token)).contains("test@example.com");
        assertThat(jwtService.getTenantId(token)).contains(tenantId.toString());
    }

    @Test
    void shouldRejectInvalidToken() {
        assertThat(jwtService.getSubject("invalid-token")).isEmpty();
    }

    @Test
    void shouldExtractRolesCorrectly() {
        Set<String> roles = Set.of("ADMIN", "ANALYST");
        String token = jwtService.generateToken("test@example.com", UUID.randomUUID(), roles);

        // FIXED: Use correct AssertJ method
        assertThat(jwtService.getRoles(token)).containsExactlyInAnyOrder("ADMIN", "ANALYST");
    }

    @Test
    void shouldHandleEmptyRoles() {
        String token = jwtService.generateToken("test@example.com", UUID.randomUUID(), Set.of());

        assertThat(jwtService.getRoles(token)).isEmpty();
    }

    @Test
    void shouldHandleSingleRole() {
        String token = jwtService.generateToken("test@example.com", UUID.randomUUID(), Set.of("ANALYST"));

        assertThat(jwtService.getRoles(token)).containsExactly("ANALYST");
    }

    @Test
    void shouldValidateTokenExpiration() {
        // Test with valid token that should not be expired
        String token = jwtService.generateToken("test@example.com", UUID.randomUUID(), Set.of("ADMIN"));

        assertThat(jwtService.isTokenExpired(token)).isFalse();
    }

    @Test
    void shouldExtractTenantIdCorrectly() {
        UUID expectedTenantId = UUID.fromString("12345678-1234-1234-1234-123456789012");
        String token = jwtService.generateToken("test@example.com", expectedTenantId, Set.of("ADMIN"));

        assertThat(jwtService.getTenantId(token))
                .isPresent()
                .contains(expectedTenantId.toString());
    }

    @Test
    void shouldHandleNullInputsGracefully() {
        // Test with null email
        assertThat(jwtService.getSubject(null)).isEmpty();

        // Test with empty string
        assertThat(jwtService.getSubject("")).isEmpty();
    }
}