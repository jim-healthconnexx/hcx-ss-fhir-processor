package com.hcx.fhir.processor;

import com.hcx.fhir.processor.config.AwsS3Properties;
import com.hcx.fhir.processor.config.FhirProperties;
import com.hcx.fhir.processor.config.SecretsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

// HDC-175: Entry point for the SureScripts FHIR downloader ECS task.
@SpringBootApplication
@EnableConfigurationProperties({SecretsProperties.class, AwsS3Properties.class, FhirProperties.class})
public class HcxSsFhirProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(HcxSsFhirProcessorApplication.class, args);
    }
}
