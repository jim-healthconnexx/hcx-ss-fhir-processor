package com.hcx.fhir.processor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hcx.fhir.processor.model.DbCredentials;
import com.hcx.fhir.processor.model.KeystorePasswordSecret;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

// HDC-175: Fetches and deserializes secrets from AWS Secrets Manager.
@Slf4j
@Service
@RequiredArgsConstructor
public class SecretsService {

    private final SecretsManagerClient secretsManagerClient;
    private final ObjectMapper objectMapper;

    // HDC-175: Deserializes the DB credentials JSON secret into DbCredentials.
    public DbCredentials getDbCredentials(String arn) {
        log.debug("HDC-175: Fetching DB credentials arn={}", arn);
        String secretString = fetchSecret(arn);
        try {
            DbCredentials creds = objectMapper.readValue(secretString, DbCredentials.class);
            log.debug("HDC-175: Retrieved DB credentials host={}", creds.getHost());
            return creds;
        } catch (Exception e) {
            log.error("HDC-175: Failed to deserialize DB credentials arn={}", arn, e);
            throw new RuntimeException("HDC-175: Failed to deserialize DB credentials", e);
        }
    }

    // HDC-212: Returns the keystore password from Secrets Manager.
    // Attempts JSON deserialization first (AWS console stores single-value secrets as JSON).
    // Falls back to the trimmed raw string for plain-text secrets.
    public String getKeystorePassword(String arn) {
        log.debug("HDC-212: Fetching keystore password arn={}", arn);
        String raw = fetchSecret(arn).trim();
        try {
            KeystorePasswordSecret secret = objectMapper.readValue(raw, KeystorePasswordSecret.class);
            if (secret.getKeystorepassword() != null) {
                log.debug("HDC-212: Extracted keystore password from JSON secret arn={}", arn);
                return secret.getKeystorepassword();
            }
        } catch (Exception e) {
            log.debug("HDC-212: Secret is not JSON, using raw string arn={}", arn);
        }
        return raw;
    }

    // HDC-175: Returns the raw secret string (used for keystore password).
    @Deprecated
    public String getSecretString(String arn) {
        log.debug("HDC-175: Fetching secret arn={}", arn);
        return fetchSecret(arn);
    }

    private String fetchSecret(String arn) {
        try {
            return secretsManagerClient.getSecretValue(
                    GetSecretValueRequest.builder().secretId(arn).build()
            ).secretString();
        } catch (Exception e) {
            log.error("HDC-175: Failed to fetch secret arn={}", arn, e);
            throw new RuntimeException("HDC-175: Failed to fetch secret: " + arn, e);
        }
    }
}
