package com.hcx.fhir.processor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

// HDC-175: Maps the JSON structure of the DB credentials secret from Secrets Manager.
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DbCredentials {

    private String host;
    private String port;
    private String dbname;
    private String username;
    private String password;
}
