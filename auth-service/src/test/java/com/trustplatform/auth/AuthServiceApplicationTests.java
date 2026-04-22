package com.trustplatform.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(VerificationFlowIntegrationTest.FakeS3StorageTestConfig.class)
class AuthServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
