package com.hcx.fhir.processor.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

// HDC-175: AWS client beans. DefaultCredentialsProvider picks up the ECS task IAM role automatically.
@Slf4j
@Configuration
public class AwsConfig {

    private final String region;

    public AwsConfig(@Value("${aws.region:us-east-1}") String region) {
        this.region = region;
    }

    @Bean
    public SecretsManagerClient secretsManagerClient() {
        log.info("HDC-175: Creating SecretsManagerClient for region={}", region);
        return SecretsManagerClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public S3Client s3Client() {
        log.info("HDC-175: Creating S3Client for region={}", region);
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
