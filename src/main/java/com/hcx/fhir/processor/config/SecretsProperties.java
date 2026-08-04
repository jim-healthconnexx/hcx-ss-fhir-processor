package com.hcx.fhir.processor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

// HDC-175: AWS Secrets Manager ARNs bound from aws.secrets.*
@ConfigurationProperties(prefix = "aws.secrets")
@Data
public class SecretsProperties {

    /**
     * ARN for the DB credentials secret (JSON with host, port, dbname, username, password).
     * @deprecated HDC-213: DB credentials are now injected by DbSecretsEnvironmentPostProcessor.
     *             This ARN is still read directly from the environment by that processor.
     */
    @Deprecated
    private String dbCredentialsArn;

    /** ARN for the SureScripts keystore password secret (raw string). */
    private String keystorePasswordArn;
}
