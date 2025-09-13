// ==============================================================================
// Complete Integration Test Suite
// Create: backend/src/test/java/com/payshield/frauddetector/integration/
// ==============================================================================

package com.payshield.frauddetector.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payshield.frauddetector.config.JwtService;
import com.payshield.frauddetector.infrastructure.jpa.SpringUserRepository;
import com.payshield.frauddetector.infrastructure.jpa.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
class PayShieldIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private SpringUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private String authToken;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // Create test user and get auth token
        tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        createTestUserAndAuthenticate();
    }

    @Test
    void authenticationFlow_ShouldWork() throws Exception {
        // Test login
        String loginRequest = """
            {
                "email": "test@example.com",
                "password": "testpassword"
            }
            """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.user.email").value("test@example.com"));

        // Test whoami with valid token
        mockMvc.perform(get("/auth/whoami")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("test@example.com"))
                .andExpect(jsonPath("$.roles").isArray());
    }

    @Test
    void fileUpload_ShouldWork() throws Exception {
        byte[] pdfContent = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test-invoice.pdf", "application/pdf", pdfContent);

        String metadata = """
            {
                "vendorName": "Test Vendor Corp",
                "currency": "USD"
            }
            """;

        MockMultipartFile metaPart = new MockMultipartFile(
                "meta", "", "application/json", metadata.getBytes());

        mockMvc.perform(multipart("/invoices/upload")
                        .file(file)
                        .file(metaPart)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-Id", tenantId.toString()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void fraudDetectionEndpoints_ShouldWork() throws Exception {
        // Test IBAN validation
        String ibanTest = """
            {
                "iban": "GB29NWBK60161331926819"
            }
            """;

        mockMvc.perform(post("/fraud/test/iban-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ibanTest)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-Id", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").isBoolean())
                .andExpect(jsonPath("$.riskScore").isNumber());

        // Test comprehensive analysis
        String comprehensiveTest = """
            {
                "vendorName": "Acme Corporation Ltd",
                "iban": "GB29NWBK60161331926819",
                "amount": "1247.83",
                "currency": "GBP",
                "senderDomain": "acme.com"
            }
            """;

        mockMvc.perform(post("/fraud/test/comprehensive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(comprehensiveTest)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Tenant-Id", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis.flagged").isBoolean())
                .andExpect(jsonPath("$.analysis.riskLevel").exists());
    }

    @Test
    void adminEndpoints_ShouldRequireAdminRole() throws Exception {
        // Test encryption status (admin only)
        mockMvc.perform(get("/admin/encryption/status")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Test system health
        mockMvc.perform(get("/admin/system/health")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallStatus").exists());
    }

    @Test
    void mfaEndpoints_ShouldWork() throws Exception {
        // Test MFA status
        mockMvc.perform(get("/mfa/status")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSetup").isBoolean())
                .andExpect(jsonPath("$.isEnabled").isBoolean());

        // Test MFA setup initiation
        mockMvc.perform(post("/mfa/setup/initiate")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrCodeUri").exists())
                .andExpect(jsonPath("$.backupCodes").isArray());
    }

    @Test
    void unauthorizedAccess_ShouldBeForbidden() throws Exception {
        // Test without token
        mockMvc.perform(get("/invoices"))
                .andExpect(status().isUnauthorized());

        // Test with invalid token
        mockMvc.perform(get("/invoices")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsHeaders_ShouldBePresent() throws Exception {
        mockMvc.perform(options("/auth/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"));
    }

    private void createTestUserAndAuthenticate() {
        // Create test user
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setPasswordHash(passwordEncoder.encode("testpassword"));
        user.setTenantId(tenantId);
        user.setRoles(Set.of("ADMIN", "ANALYST", "APPROVER"));
        user.setMfaEnabled(false);
        userRepository.save(user);

        // Generate auth token
        authToken = jwtService.generateToken(user.getEmail(), user.getTenantId(), user.getRoles());
    }
}