package com.hcx.fhir.processor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

// HDC-175: SureScripts FHIR API base URL bound from surescripts.fhir.*
@ConfigurationProperties(prefix = "surescripts.fhir")
@Data
public class FhirProperties {

    /** Base URL for the SureScripts FHIR API (e.g. https://staging.care-coordination.surescripts.net/ext/v1). */
    private String baseUrl;
}
