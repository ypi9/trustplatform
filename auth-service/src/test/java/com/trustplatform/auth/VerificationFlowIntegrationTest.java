package com.trustplatform.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class VerificationFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String userToken;
    private String requestId;
    private String secondUserToken;
    private String secondRequestId;

    @BeforeAll
    void cleanupBefore() {
        jdbcTemplate.execute("DELETE FROM verification_requests WHERE user_id IN (SELECT id FROM users WHERE email IN ('flowtest5@test.com', 'flowtest6@test.com'))");
        jdbcTemplate.execute("DELETE FROM audit_log WHERE metadata LIKE '%flowtest5@test.com%' OR metadata LIKE '%flowtest6@test.com%'");
        jdbcTemplate.execute("DELETE FROM user_profile WHERE user_id IN (SELECT id FROM users WHERE email IN ('flowtest5@test.com', 'flowtest6@test.com'))");
        jdbcTemplate.execute("DELETE FROM users WHERE email IN ('flowtest5@test.com', 'flowtest6@test.com')");
    }

    // ──────────────────────────────────────────────
    // FLOW A: Submit → Approve → Verified
    // ──────────────────────────────────────────────

    @Test
    @Order(1)
    void registerUser() throws Exception {
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"flowtest5@test.com\", \"password\": \"password123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(2)
    void loginUser() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"flowtest5@test.com\", \"password\": \"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        userToken = json.get("accessToken").asText();
    } 

    @Test
    @Order(3)
    void initialStatusIsNone() throws Exception {
        mockMvc.perform(get("/auth/me")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.verificationLevel").value("NONE"));
    }

    @Test
    @Order(4)
    void submitVerificationSuccess() throws Exception {
        MvcResult result = mockMvc.perform(post("/verification/submit")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentUrl\": \"s3://fake-bucket/test-id-card.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        requestId = json.get("requestId").asText();
    }

    @Test
    @Order(5)
    void statusIsPendingAfterSubmit() throws Exception {
        mockMvc.perform(get("/verification/status")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationLevel").value("PENDING"))
                .andExpect(jsonPath("$.latestRequest.status").value("PENDING"));
    }

    @Test
    @Order(6)
    void duplicatePendingSubmissionBlocked() throws Exception {
        mockMvc.perform(post("/verification/submit")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentUrl\": \"s3://fake-bucket/another-doc.png\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(7)
    void approveRequestUpdatesProfile() throws Exception {
        mockMvc.perform(post("/verification/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\": \"" + requestId + "\", \"decision\": \"APPROVED\", \"reviewNotes\": \"Looks good\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.userVerificationLevel").value("VERIFIED"));
    }

    @Test
    @Order(8)
    void profileIsVerifiedAfterApproval() throws Exception {
        mockMvc.perform(get("/auth/me")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.verificationLevel").value("VERIFIED"));
    }

    @Test
    @Order(9)
    void verifiedUserCannotSubmitAgain() throws Exception {
        mockMvc.perform(post("/verification/submit")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentUrl\": \"s3://fake-bucket/yet-another.png\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(10)
    void reviewAlreadyReviewedRequestBlocked() throws Exception {
        mockMvc.perform(post("/verification/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\": \"" + requestId + "\", \"decision\": \"REJECTED\", \"reviewNotes\": \"Changed mind\"}"))
                .andExpect(status().isConflict());
    }

    // ──────────────────────────────────────────────
    // FLOW B: Submit → Reject → Rejected
    // ──────────────────────────────────────────────

    @Test
    @Order(11)
    void registerSecondUser() throws Exception {
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"flowtest6@test.com\", \"password\": \"password123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(12)
    void loginSecondUser() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"flowtest6@test.com\", \"password\": \"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        secondUserToken = json.get("accessToken").asText();
    }

    @Test
    @Order(13)
    void secondUserSubmitsVerification() throws Exception {
        MvcResult result = mockMvc.perform(post("/verification/submit")
                .header("Authorization", "Bearer " + secondUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentUrl\": \"s3://fake-bucket/bob-id-card.png\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        secondRequestId = json.get("requestId").asText();
    }

    @Test
    @Order(14)
    void rejectRequestUpdatesProfile() throws Exception {
        mockMvc.perform(post("/verification/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\": \"" + secondRequestId + "\", \"decision\": \"REJECTED\", \"reviewNotes\": \"Blurry document\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.userVerificationLevel").value("REJECTED"));
    }

    @Test
    @Order(15)
    void profileIsRejectedAfterRejection() throws Exception {
        mockMvc.perform(get("/auth/me")
                .header("Authorization", "Bearer " + secondUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.verificationLevel").value("REJECTED"));
    }

    // ──────────────────────────────────────────────
    // FLOW C: Resubmit after rejection
    // ──────────────────────────────────────────────

    @Test
    @Order(16)
    void rejectedUserCanResubmit() throws Exception {
        MvcResult result = mockMvc.perform(post("/verification/submit")
                .header("Authorization", "Bearer " + secondUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentUrl\": \"s3://fake-bucket/bob-id-card-v2.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        secondRequestId = json.get("requestId").asText();
    }

    @Test
    @Order(17)
    void profileBackToPendingAfterResubmit() throws Exception {
        mockMvc.perform(get("/auth/me")
                .header("Authorization", "Bearer " + secondUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.verificationLevel").value("PENDING"));
    }

    // ──────────────────────────────────────────────
    // Edge cases
    // ──────────────────────────────────────────────

    @Test
    @Order(18)
    void submitWithMissingDocumentUrlReturns400() throws Exception {
        mockMvc.perform(post("/verification/submit")
                .header("Authorization", "Bearer " + secondUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(19)
    void submitWithNoTokenReturns401Or403() throws Exception {
        mockMvc.perform(post("/verification/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentUrl\": \"s3://fake-bucket/no-auth.png\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(20)
    void listRequestsReturnsAll() throws Exception {
        mockMvc.perform(get("/verification/requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @Order(21)
    void listRequestsFilterByPending() throws Exception {
        mockMvc.perform(get("/verification/requests").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @AfterAll
    void cleanup() {
        jdbcTemplate.execute("DELETE FROM verification_requests WHERE user_id IN (SELECT id FROM users WHERE email IN ('flowtest5@test.com', 'flowtest6@test.com'))");
        jdbcTemplate.execute("DELETE FROM audit_log WHERE metadata LIKE '%flowtest5@test.com%' OR metadata LIKE '%flowtest6@test.com%'");
        jdbcTemplate.execute("DELETE FROM user_profile WHERE user_id IN (SELECT id FROM users WHERE email IN ('flowtest5@test.com', 'flowtest6@test.com'))");
        jdbcTemplate.execute("DELETE FROM users WHERE email IN ('flowtest5@test.com', 'flowtest6@test.com')");
    }
}