package com.hcx.fhir.processor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

// HDC-175: SureScripts FHIR API base URL bound from surescripts.fhir.*
@ConfigurationProperties(prefix = "surescripts.fhir")
@Data
public class FhirProperties {

    /** Base URL for the SureScripts FHIR API (e.g. https://staging.care-coordination.surescripts.net/ext/v1). */
    private String baseUrl;

    /** HDC-239: When true, fetch and store the SureScripts CapabilityStatement on each run. */
    private boolean capabilitiesEnabled = false;

    /** HDC-245: Full URL for the SureScripts CapabilityStatement endpoint (varies per environment). */
    private String capabilitiesUrl;

    /** HDC-261: Number of resources to request per FHIR page via _count parameter. */
    private int pageCount = 100;
}
