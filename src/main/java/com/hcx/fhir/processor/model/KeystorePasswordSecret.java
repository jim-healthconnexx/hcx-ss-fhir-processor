package com.hcx.fhir.processor.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

// HDC-212: Maps the JSON structure of the keystore password secret from Secrets Manager.
// Supports both "keystorepassword" (AWS console default) and "password" key variants.
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KeystorePasswordSecret {

    @JsonAlias("password")
    private String keystorepassword;
}
