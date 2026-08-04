package com.hcx.fhir.processor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

// HDC-175: Maps the JSON structure of the DB credentials secret from Secrets Manager.
// @Deprecated HDC-213: Replaced by DbSecretsEnvironmentPostProcessor which injects spring.datasource.* directly.
@Deprecated
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DbCredentials {

    private String host;
    private String port;
    private String dbname;
    private String username;
    private String password;
}
