package com.hcx.fhir.processor.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

// HDC-212: Maps the JSON structure of the keystore password secret from Secrets Manager.
// Primary key is "keystore-password" (hyphenated, as stored in AWS Secrets Manager).
// Aliases cover other common variants for forward compatibility.
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KeystorePasswordSecret {

    @JsonProperty("keystore-password")
    @JsonAlias({"keystorepassword", "password"})
    private String keystorePassword;
}
