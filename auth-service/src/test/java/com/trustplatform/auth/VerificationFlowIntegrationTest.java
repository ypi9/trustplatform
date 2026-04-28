package com.trustplatform.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustplatform.auth.auth.security.JwtService;
import com.trustplatform.auth.storage.dto.S3BucketInfo;
import com.trustplatform.auth.storage.dto.S3UploadResult;
import com.trustplatform.auth.storage.service.S3StorageService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end integration tests for the TrustPlatform Auth Service.
 *
 * Flow A (approve):  register → login → upload → submit → admin approve → VERIFIED
 * Flow B (reject):   register → login → upload → submit → admin reject  → REJECTED
 * Flow C (resubmit): upload new file → resubmit → back to PENDING
 *
 * Also covers RBAC (regular user blocked from admin endpoints) and edge cases.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class VerificationFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtService jwtService;

    // ── Shared state across ordered tests ──
    private String userToken;
    private String adminToken;
    private String fileUrl;
    private String requestId;

    private String secondUserToken;
    private String secondFileUrl;
    private String secondRequestId;

    private static final String USER_EMAIL  = "e2e-user@test.com";
    private static final String ADMIN_EMAIL = "e2e-admin@test.com";
    private static final String USER2_EMAIL = "e2e-user2@test.com";
    private static final String PASSWORD    = "password123";
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "application/pdf"
    );
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final byte[] PNG_BYTES = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00
    };
    private static final byte[] JPEG_BYTES = new byte[] {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00
    };
    private static final byte[] PDF_BYTES = "%PDF-1.7\n".getBytes();

    // ── Cleanup before and after ──

    @BeforeAll
    void cleanupBefore() {
        cleanTestData();
    }

    @AfterAll
    void cleanupAfter() {
        cleanTestData();
    }

    private void cleanTestData() {
        String[] emails = {USER_EMAIL, ADMIN_EMAIL, USER2_EMAIL};
        for (String email : emails) {
            jdbcTemplate.execute(
                "DELETE FROM verification_requests WHERE user_id IN " +
                "(SELECT id FROM users WHERE email = '" + email + "')");
            jdbcTemplate.execute(
                "DELETE FROM audit_log WHERE metadata LIKE '%" + email + "%'");
            jdbcTemplate.execute(
                "DELETE FROM user_profile WHERE user_id IN " +
                "(SELECT id FROM users WHERE email = '" + email + "')");
            jdbcTemplate.execute(
                "DELETE FROM users WHERE email = '" + email + "'");
        }
    }

    // ══════════════════════════════════════════════
    //  SETUP: Register admin + regular user
    // ══════════════════════════════════════════════

    @Test @Order(1)
    void registerAdmin() throws Exception {
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + ADMIN_EMAIL + "\", \"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());

        // Promote to ADMIN via direct DB update
        jdbcTemplate.execute(
            "UPDATE users SET role = 'ADMIN' WHERE email = '" + ADMIN_EMAIL + "'");
    }

    @Test @Order(2)
    void loginAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + ADMIN_EMAIL + "\", \"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        adminToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();

        String adminUserId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM users WHERE email = ?",
                String.class,
                ADMIN_EMAIL
        );

        Assertions.assertEquals(ADMIN_EMAIL, jwtService.extractEmail(adminToken));
        Assertions.assertEquals(ADMIN_EMAIL, jwtService.extractEmailClaim(adminToken));
        Assertions.assertEquals(adminUserId, jwtService.extractUserId(adminToken));
        Assertions.assertEquals("ADMIN", jwtService.extractRole(adminToken));
    }

    @Test @Order(3)
    void registerUser() throws Exception {
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + USER_EMAIL + "\", \"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test @Order(4)
    void loginUser() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + USER_EMAIL + "\", \"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        userToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();

        String userId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM users WHERE email = ?",
                String.class,
                USER_EMAIL
        );

        Assertions.assertEquals(USER_EMAIL, jwtService.extractEmail(userToken));
        Assertions.assertEquals(USER_EMAIL, jwtService.extractEmailClaim(userToken));
        Assertions.assertEquals(userId, jwtService.extractUserId(userToken));
        Assertions.assertEquals("USER", jwtService.extractRole(userToken));
    }

    @Test @Order(5)
    void requestCorrelationIdIsEchoedInResponseHeader() throws Exception {
        String correlationId = UUID.randomUUID().toString();

        mockMvc.perform(post("/auth/login")
                .header("X-Request-Id", correlationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + USER_EMAIL + "\", \"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", correlationId));
    }

    @Test @Order(6)
    void loginIsCaseInsensitiveForEmail() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + USER_EMAIL.toUpperCase() + "\", \"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test @Order(7)
    void actuatorHealthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // ══════════════════════════════════════════════
    //  FLOW A: Upload → Submit → Approve → VERIFIED
    // ══════════════════════════════════════════════

    @Test @Order(10)
    void initialStatusIsNone() throws Exception {
        mockMvc.perform(get("/auth/me")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.verificationLevel").value("NONE"));
    }

    @Test @Order(11)
    void uploadFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "id-card.png", "image/png", PNG_BYTES);

        MvcResult result = mockMvc.perform(multipart("/files/upload")
                .file(file)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileUrl").exists())
                .andExpect(jsonPath("$.objectKey").exists())
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.bucket").value("test-bucket"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.size").value(PNG_BYTES.length))
                .andReturn();

        var uploadJson = objectMapper.readTree(result.getResponse().getContentAsString());
        fileUrl = uploadJson.get("fileUrl").asText();
        String objectKey = uploadJson.get("objectKey").asText();
        String uploadRequestId = uploadJson.get("requestId").asText();
        Assertions.assertEquals(objectKey, fileUrl);
        Assertions.assertTrue(fileUrl.startsWith("verification/"));
        Assertions.assertTrue(fileUrl.contains("/" + uploadRequestId + "/"));
        Assertions.assertFalse(fileUrl.startsWith("http://"));
        Assertions.assertFalse(fileUrl.startsWith("https://"));
    }

    @Test @Order(12)
    void submitVerification() throws Exception {
        MvcResult result = mockMvc.perform(post("/verification/submit")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentKey\": \"" + fileUrl + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.documentKey").value(fileUrl))
                .andExpect(jsonPath("$.documentOriginalName").value("id-card.png"))
                .andExpect(jsonPath("$.documentContentType").value("image/png"))
                .andExpect(jsonPath("$.documentSize").value(PNG_BYTES.length))
                .andExpect(jsonPath("$.documentUrl").value(fileUrl))
                .andReturn();

        requestId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("requestId").asText();
    }

    @Test @Order(13)
    void databaseStoresS3ObjectMetadata() {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT document_key, document_url, document_original_name, document_content_type, document_size " +
                        "FROM verification_requests WHERE id = ?",
                java.util.UUID.fromString(requestId)
        );

        Assertions.assertEquals(fileUrl, row.get("document_key"));
        Assertions.assertEquals(fileUrl, row.get("document_url"));
        Assertions.assertFalse(((String) row.get("document_url")).startsWith("http://"));
        Assertions.assertFalse(((String) row.get("document_url")).startsWith("https://"));
        Assertions.assertEquals("id-card.png", row.get("document_original_name"));
        Assertions.assertEquals("image/png", row.get("document_content_type"));
        Assertions.assertEquals((long) PNG_BYTES.length, ((Number) row.get("document_size")).longValue());
    }

    @Test @Order(14)
    void statusIsPendingAfterSubmit() throws Exception {
        mockMvc.perform(get("/verification/status")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationLevel").value("PENDING"))
                .andExpect(jsonPath("$.latestRequest.status").value("PENDING"));
    }

    @Test @Order(15)
    void duplicatePendingSubmissionBlocked() throws Exception {
        mockMvc.perform(post("/verification/submit")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentKey\": \"" + fileUrl + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("User already has a pending verification request"))
                .andExpect(jsonPath("$.path").value("/verification/submit"));
    }

    @Test @Order(16)
    void regularUserCannotAccessAdminListEndpoint() throws Exception {
        mockMvc.perform(get("/verification/requests")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("You do not have permission to access this resource"))
                .andExpect(jsonPath("$.path").value("/verification/requests"));
    }

    @Test @Order(17)
    void regularUserCannotAccessAdminReviewEndpoint() throws Exception {
        mockMvc.perform(post("/verification/review")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\": \"" + requestId
                        + "\", \"decision\": \"APPROVED\", \"reviewNotes\": \"test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test @Order(18)
    void regularUserCannotGenerateDocumentLink() throws Exception {
        mockMvc.perform(get("/verification/requests/" + requestId + "/document-link")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test @Order(19)
    void adminListsPendingRequests() throws Exception {
        mockMvc.perform(get("/verification/requests").param("status", "PENDING")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.requestId == '" + requestId + "')]").exists())
                .andExpect(jsonPath("$[?(@.documentKey == '" + fileUrl + "')]").exists());
    }

    @Test @Order(20)
    void adminGeneratesPresignedDocumentLink() throws Exception {
        mockMvc.perform(get("/verification/requests/" + requestId + "/document-link")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.downloadUrl").value("https://example.test/presigned/" + fileUrl));
    }

    @Test @Order(21)
    void adminApprovesRequest() throws Exception {
        mockMvc.perform(post("/verification/review")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\": \"" + requestId
                        + "\", \"decision\": \"APPROVED\""
                        + ", \"reviewNotes\": \"Document looks good\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.userVerificationLevel").value("VERIFIED"))
                .andExpect(jsonPath("$.reviewNotes").value("Document looks good"))
                .andExpect(jsonPath("$.reviewedAt").exists());
    }

    @Test @Order(22)
    void profileIsVerifiedAfterApproval() throws Exception {
        mockMvc.perform(get("/auth/me")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.verificationLevel").value("VERIFIED"));
    }

    @Test @Order(23)
    void verifiedUserCannotSubmitAgain() throws Exception {
        mockMvc.perform(post("/verification/submit")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentKey\": \"" + fileUrl + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test @Order(24)
    void reviewAlreadyReviewedRequestBlocked() throws Exception {
        mockMvc.perform(post("/verification/review")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\": \"" + requestId
                        + "\", \"decision\": \"REJECTED\""
                        + ", \"reviewNotes\": \"Changed mind\"}"))
                .andExpect(status().isConflict());
    }

    // ══════════════════════════════════════════════
    //  FLOW B: Upload → Submit → Reject → REJECTED
    // ══════════════════════════════════════════════

    @Test @Order(30)
    void registerSecondUser() throws Exception {
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + USER2_EMAIL + "\", \"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test @Order(31)
    void loginSecondUser() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + USER2_EMAIL + "\", \"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        secondUserToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    @Test @Order(32)
    void secondUserUploadsFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "passport.pdf", "application/pdf", PDF_BYTES);

        MvcResult result = mockMvc.perform(multipart("/files/upload")
                .file(file)
                .header("Authorization", "Bearer " + secondUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileUrl").exists())
                .andReturn();

        secondFileUrl = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("fileUrl").asText();
    }

    @Test @Order(33)
    void secondUserSubmitsVerification() throws Exception {
        MvcResult result = mockMvc.perform(post("/verification/submit")
                .header("Authorization", "Bearer " + secondUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentKey\": \"" + secondFileUrl + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        secondRequestId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("requestId").asText();
    }

    @Test @Order(34)
    void adminRejectsRequest() throws Exception {
        mockMvc.perform(post("/verification/review")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\": \"" + secondRequestId
                        + "\", \"decision\": \"REJECTED\""
                        + ", \"reviewNotes\": \"Blurry document\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.userVerificationLevel").value("REJECTED"))
                .andExpect(jsonPath("$.reviewNotes").value("Blurry document"));
    }

    @Test @Order(35)
    void profileIsRejectedAfterRejection() throws Exception {
        mockMvc.perform(get("/auth/me")
                .header("Authorization", "Bearer " + secondUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.verificationLevel").value("REJECTED"));
    }

    // ══════════════════════════════════════════════
    //  FLOW C: Resubmit after rejection → PENDING
    // ══════════════════════════════════════════════

    @Test @Order(40)
    void rejectedUserUploadsNewFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "passport-v2.jpg", "image/jpeg", JPEG_BYTES);

        MvcResult result = mockMvc.perform(multipart("/files/upload")
                .file(file)
                .header("Authorization", "Bearer " + secondUserToken))
                .andExpect(status().isOk())
                .andReturn();

        secondFileUrl = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("fileUrl").asText();
    }

    @Test @Order(41)
    void rejectedUserCanResubmit() throws Exception {
        MvcResult result = mockMvc.perform(post("/verification/submit")
                .header("Authorization", "Bearer " + secondUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentKey\": \"" + secondFileUrl + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        secondRequestId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("requestId").asText();
    }

    @Test @Order(42)
    void profileBackToPendingAfterResubmit() throws Exception {
        mockMvc.perform(get("/auth/me")
                .header("Authorization", "Bearer " + secondUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.verificationLevel").value("PENDING"));
    }

    // ══════════════════════════════════════════════
    //  EDGE CASES
    // ══════════════════════════════════════════════

    @Test @Order(50)
    void submitWithMissingDocumentKeyReturns400() throws Exception {
        mockMvc.perform(post("/verification/submit")
                .header("Authorization", "Bearer " + secondUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("documentKey is required"))
                .andExpect(jsonPath("$.path").value("/verification/submit"));
    }

    @Test @Order(51)
    void submitWithNoTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/verification/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentKey\": \"uploads/fake.png\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource"))
                .andExpect(jsonPath("$.path").value("/verification/submit"));
    }

    @Test @Order(52)
    void submitWithNonExistentFileReturns400() throws Exception {
        mockMvc.perform(post("/verification/submit")
                .header("Authorization", "Bearer " + secondUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentKey\": \"uploads/does-not-exist.png\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(53)
    void uploadInvalidFileTypeReturns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "malware.exe", "application/octet-stream", "bad-content".getBytes());

        mockMvc.perform(multipart("/files/upload")
                .file(file)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(54)
    void uploadWithNoTokenReturnsUnauthorized() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "id.png", "image/png", PNG_BYTES);

        mockMvc.perform(multipart("/files/upload").file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource"))
                .andExpect(jsonPath("$.path").value("/files/upload"));
    }

    @Test @Order(55)
    void uploadOversizedFileReturns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "too-large.pdf", "application/pdf", new byte[(int) MAX_FILE_SIZE + 1]);

        mockMvc.perform(multipart("/files/upload")
                .file(file)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(56)
    void uploadSpoofedPngReturns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "malware.png", "image/png", "not-a-real-png".getBytes());

        mockMvc.perform(multipart("/files/upload")
                .file(file)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(57)
    void submitWithPublicDocumentUrlReturns400() throws Exception {
        mockMvc.perform(post("/verification/submit")
                .header("Authorization", "Bearer " + secondUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentKey\": \"https://example.test/public/id-card.png\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(58)
    void duplicateSignupReturnsConflict() throws Exception {
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + USER_EMAIL + "\", \"password\": \"password999\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Email already exists"))
                .andExpect(jsonPath("$.path").value("/auth/signup"));
    }

    @Test @Order(59)
    void duplicateSignupIgnoringEmailCaseReturnsConflict() throws Exception {
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + USER_EMAIL.toUpperCase() + "\", \"password\": \"password999\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already exists"));
    }

    @Test @Order(60)
    void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + USER_EMAIL + "\", \"password\": \"wrongpassword\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.path").value("/auth/login"));
    }

    @Test @Order(61)
    void signupValidationErrorsUseStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"not-an-email\", \"password\": \"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.path").value("/auth/signup"))
                .andExpect(jsonPath("$.fields.email").value("Invalid email format"))
                .andExpect(jsonPath("$.fields.password").value("Password must be at least 8 characters"));
    }

    @Test @Order(62)
    void adminListsAllRequests() throws Exception {
        mockMvc.perform(get("/verification/requests")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test @Order(63)
    void actuatorMetricsExposeCustomCounters() throws Exception {
        mockMvc.perform(get("/actuator/metrics/trustplatform.auth.logins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("trustplatform.auth.logins"))
                .andExpect(jsonPath("$.measurements[0].value").isNumber());

        mockMvc.perform(get("/actuator/metrics/trustplatform.verification.requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("trustplatform.verification.requests"))
                .andExpect(jsonPath("$.measurements[0].value").isNumber());

        mockMvc.perform(get("/actuator/metrics/trustplatform.verification.approvals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("trustplatform.verification.approvals"))
                .andExpect(jsonPath("$.measurements[0].value").isNumber());

        mockMvc.perform(get("/actuator/metrics/trustplatform.verification.rejections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("trustplatform.verification.rejections"))
                .andExpect(jsonPath("$.measurements[0].value").isNumber());
    }

    @TestConfiguration
    static class FakeS3StorageTestConfig {
        @Bean
        @Primary
        S3StorageService fakeS3StorageService() {
            return new FakeS3StorageService();
        }
    }

    static class FakeS3StorageService extends S3StorageService {
        private final Map<String, S3UploadResult> uploadedObjects = new HashMap<>();

        FakeS3StorageService() {
            super(null, null, "test-bucket");
        }

        @Override
        public S3UploadResult upload(MultipartFile file, UUID userId, UUID requestId) {
            S3UploadResult result = validateAndStoreFakeUpload(file, userId, requestId);
            uploadedObjects.put(result.getObjectKey(), result);
            return result;
        }

        @Override
        public S3UploadResult getObjectMetadata(String objectKey) {
            S3UploadResult result = uploadedObjects.get(normalizeKey(objectKey));
            if (result == null) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "File not found. Please upload a file first via POST /files/upload");
            }
            return result;
        }

        @Override
        public URL generatePresignedGetUrl(String objectKey, Duration expiresIn) {
            try {
                return new URL("https://example.test/presigned/" + normalizeKey(objectKey));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public S3BucketInfo validateBucketAccess(int maxKeys) {
            return S3BucketInfo.builder()
                    .bucket("test-bucket")
                    .region("us-east-2")
                    .accessible(true)
                    .objectCount(uploadedObjects.size())
                    .sampleKeys(uploadedObjects.keySet().stream().limit(maxKeys).toList())
                    .checkedAt(Instant.now())
                    .build();
        }

        @Override
        public boolean isBucketPublicAccessBlocked() {
            return true;
        }

        private S3UploadResult validateAndStoreFakeUpload(MultipartFile file, UUID userId, UUID requestId) {
            if (file == null || file.isEmpty()) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "File is empty");
            }
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "File size exceeds the 5 MB limit");
            }
            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "File type not allowed. Accepted types: png, jpg, pdf");
            }
            validateFileSignature(file, contentType);

            String extension = switch (contentType) {
                case "image/png" -> ".png";
                case "image/jpeg" -> ".jpg";
                case "application/pdf" -> ".pdf";
                default -> "";
            };
            String objectKey = "verification/" + userId + "/" + requestId + "/" + UUID.randomUUID() + extension;
            return S3UploadResult.builder()
                    .bucket("test-bucket")
                    .objectKey(objectKey)
                    .requestId(requestId)
                    .originalFilename(file.getOriginalFilename())
                    .contentType(contentType)
                    .size(file.getSize())
                    .build();
        }

        private String normalizeKey(String objectKey) {
            if (objectKey == null) {
                return null;
            }
            return objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;
        }

        private void validateFileSignature(MultipartFile file, String contentType) {
            try (InputStream inputStream = file.getInputStream()) {
                byte[] header = inputStream.readNBytes(8);
                boolean valid = switch (contentType) {
                    case "image/png" -> header.length >= 8
                            && (header[0] & 0xFF) == 0x89
                            && header[1] == 0x50
                            && header[2] == 0x4E
                            && header[3] == 0x47
                            && header[4] == 0x0D
                            && header[5] == 0x0A
                            && header[6] == 0x1A
                            && header[7] == 0x0A;
                    case "image/jpeg" -> header.length >= 3
                            && (header[0] & 0xFF) == 0xFF
                            && (header[1] & 0xFF) == 0xD8
                            && (header[2] & 0xFF) == 0xFF;
                    case "application/pdf" -> header.length >= 4
                            && header[0] == 0x25
                            && header[1] == 0x50
                            && header[2] == 0x44
                            && header[3] == 0x46;
                    default -> false;
                };

                if (!valid) {
                    throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                            "File content does not match declared content type");
                }
            } catch (IOException e) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                        "Could not read uploaded file");
            }
        }
    }
}
