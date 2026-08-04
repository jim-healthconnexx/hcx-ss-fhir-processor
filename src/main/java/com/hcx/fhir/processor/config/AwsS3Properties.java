package com.hcx.fhir.processor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

// HDC-175: AWS S3 properties bound from aws.s3.*
@ConfigurationProperties(prefix = "aws.s3")
@Data
public class AwsS3Properties {

    /** S3 bucket holding the SureScripts P12 keystore. */
    private String keystoreBucket;

    /** S3 key for the P12 keystore file (e.g. keystore/surescripts-qa.p12). */
    private String keystoreKey;

    /** S3 bucket where assembled FHIR JSON output is stored. */
    private String fhirOutputBucket;

    /** S3 key prefix (folder) prepended to each FHIR output file, e.g. "fhir/". */
    private String fhirOutputKeyPrefix = "";

    /** HDC-214: Optional S3 key for extra CA bundle (e.g. .p7b) to merge into the TrustManager.
     *  Null by default — prod environments that don't need a custom CA leave this unset. */
    private String truststoreKey;
}
