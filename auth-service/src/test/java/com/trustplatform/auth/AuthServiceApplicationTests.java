package com.trustplatform.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(VerificationFlowIntegrationTest.FakeS3StorageTestConfig.class)
class AuthServiceApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void healthReportsDatabaseAndS3() throws Exception {
		mockMvc.perform(get("/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.database").value("UP"))
				.andExpect(jsonPath("$.s3").value("UP"));
	}

	@Test
	void readinessReportsDatabaseAndS3() throws Exception {
		mockMvc.perform(get("/ready"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.database").value("UP"))
				.andExpect(jsonPath("$.s3").value("UP"));
	}

	@Test
	void livenessReportsUp() throws Exception {
		mockMvc.perform(get("/live"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

}
