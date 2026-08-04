package com.hcx.fhir.processor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// HDC-211: Explicit ObjectMapper bean — Spring Boot 4 JacksonAutoConfiguration does not register
// one as an autowire candidate in the fat-JAR runtime, causing SecretsService to fail on startup.
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
