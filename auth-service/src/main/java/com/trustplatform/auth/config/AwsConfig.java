package com.trustplatform.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * AWS configuration.
 *
 * Authentication uses the Default Credential Provider Chain, which checks
 * (in order): environment variables, system properties, ~/.aws/credentials,
 * IAM role, etc.
 *
 * For local development, configure the AWS CLI:
 *   aws configure          (sets up ~/.aws/credentials)
 *   — or —
 *   export AWS_ACCESS_KEY_ID=...
 *   export AWS_SECRET_ACCESS_KEY=...
 */
@Configuration
public class AwsConfig {

    @Value("${aws.region}")
    private String region;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
