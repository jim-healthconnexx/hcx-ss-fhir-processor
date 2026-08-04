package com.hcx.fhir.processor.service;

import com.hcx.fhir.processor.model.DbCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;

// HDC-175: Builds a JDBC URL from DB credentials and opens a Connection.
// Schema is set via currentSchema parameter so jOOQ calls use no schema prefix.
// Callers must close the returned Connection (use try-with-resources).
// @Deprecated HDC-213: Replaced by Spring Boot DataSource auto-configuration via DbSecretsEnvironmentPostProcessor.
@Deprecated
@Slf4j
@Service
public class DatabaseService {

    public String buildJdbcUrl(DbCredentials creds) {
        return String.format("jdbc:postgresql://%s:%s/%s?currentSchema=healthdata",
                creds.getHost(), creds.getPort(), creds.getDbname());
    }

    public Connection createConnection(DbCredentials creds) {
        String jdbcUrl = buildJdbcUrl(creds);
        log.debug("HDC-175: Opening DB connection url={}", jdbcUrl);
        try {
            return DriverManager.getConnection(jdbcUrl, creds.getUsername(), creds.getPassword());
        } catch (Exception e) {
            log.error("HDC-175: Failed to open DB connection url={}", jdbcUrl, e);
            throw new RuntimeException("HDC-175: Failed to connect to database", e);
        }
    }
}
