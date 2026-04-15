package com.trustplatform.auth.storage.config;

import com.trustplatform.auth.storage.service.S3StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * AWS S3 configuration.
 *
 * <p>Authentication uses the Default Credential Provider Chain, which checks
 * (in order): environment variables → system properties → ~/.aws/credentials
 * → IAM instance-profile role.</p>
 *
 * <p>For local development, run {@code aws configure} or export
 * {@code AWS_ACCESS_KEY_ID} and {@code AWS_SECRET_ACCESS_KEY}.</p>
 */
@Configuration
public class S3Config {

    @Value("${aws.region}")
    private String region;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public String s3BucketName() {
        return bucket;
    }

    @Bean
    CommandLineRunner validateS3Bucket(S3StorageService s3StorageService) {
        return args -> {
            Logger log = LoggerFactory.getLogger(S3Config.class);
            log.info("── S3 connectivity check ──");
            var info = s3StorageService.validateBucketAccess(5);
            if (info.isAccessible()) {
                log.info("✅ S3 bucket '{}' in {} is accessible ({} object(s))",
                        info.getBucket(), info.getRegion(), info.getObjectCount());
            } else {
                log.warn("❌ S3 bucket '{}' is NOT accessible: {}",
                        info.getBucket(), info.getError());
            }
        };
    }
}
