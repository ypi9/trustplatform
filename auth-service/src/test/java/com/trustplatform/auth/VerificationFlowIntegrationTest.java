package com.trustplatform.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
                "file", "id-card.png", "image/png", "fake-image-bytes".getBytes());

        MvcResult result = mockMvc.perform(multipart("/files/upload")
                .file(file)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileUrl").exists())
                .andReturn();

        fileUrl = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("fileUrl").asText();
    }

    @Test @Order(12)
    void submitVerification() throws Exception {
        MvcResult result = mockMvc.perform(post("/verification/submit")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fileUrl\": \"" + fileUrl + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.documentUrl").value(fileUrl))
                .andReturn();

        requestId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("requestId").asText();
    }

    @Test @Order(13)
    void statusIsPendingAfterSubmit() throws Exception {
        mockMvc.perform(get("/verification/status")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationLevel").value("PENDING"))
                .andExpect(jsonPath("$.latestRequest.status").value("PENDING"));
    }

    @Test @Order(14)
    void duplicatePendingSubmissionBlocked() throws Exception {
        mockMvc.perform(post("/verification/submit")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fileUrl\": \"" + fileUrl + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test @Order(15)
    void regularUserCannotAccessAdminListEndpoint() throws Exception {
        mockMvc.perform(get("/verification/requests")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test @Order(16)
    void regularUserCannotAccessAdminReviewEndpoint() throws Exception {
        mockMvc.perform(post("/verification/review")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\": \"" + requestId
                        + "\", \"decision\": \"APPROVED\", \"reviewNotes\": \"test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test @Order(17)
    void adminListsPendingRequests() throws Exception {
        mockMvc.perform(get("/verification/requests").param("status", "PENDING")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.requestId == '" + requestId + "')]").exists());
    }

    @Test @Order(18)
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

    @Test @Order(19)
    void profileIsVerifiedAfterApproval() throws Exception {
        mockMvc.perform(get("/auth/me")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.verificationLevel").value("VERIFIED"));
    }

    @Test @Order(20)
    void verifiedUserCannotSubmitAgain() throws Exception {
        mockMvc.perform(post("/verification/submit")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fileUrl\": \"" + fileUrl + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test @Order(21)
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
                "file", "passport.pdf", "application/pdf", "fake-pdf-content".getBytes());

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
                .content("{\"fileUrl\": \"" + secondFileUrl + "\"}"))
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
                "file", "passport-v2.jpg", "image/jpeg", "better-image".getBytes());

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
                .content("{\"fileUrl\": \"" + secondFileUrl + "\"}"))
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
    void submitWithMissingFileUrlReturns400() throws Exception {
        mockMvc.perform(post("/verification/submit")
                .header("Authorization", "Bearer " + secondUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(51)
    void submitWithNoTokenReturnsForbidden() throws Exception {
        mockMvc.perform(post("/verification/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fileUrl\": \"/uploads/fake.png\"}"))
                .andExpect(status().isForbidden());
    }

    @Test @Order(52)
    void submitWithNonExistentFileReturns400() throws Exception {
        mockMvc.perform(post("/verification/submit")
                .header("Authorization", "Bearer " + secondUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fileUrl\": \"/uploads/does-not-exist.png\"}"))
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
    void uploadWithNoTokenReturnsForbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "id.png", "image/png", "bytes".getBytes());

        mockMvc.perform(multipart("/files/upload").file(file))
                .andExpect(status().isForbidden());
    }

    @Test @Order(55)
    void duplicateSignupReturnsConflict() throws Exception {
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + USER_EMAIL + "\", \"password\": \"password999\"}"))
                .andExpect(status().isConflict());
    }

    @Test @Order(56)
    void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + USER_EMAIL + "\", \"password\": \"wrongpassword\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(57)
    void adminListsAllRequests() throws Exception {
        mockMvc.perform(get("/verification/requests")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
