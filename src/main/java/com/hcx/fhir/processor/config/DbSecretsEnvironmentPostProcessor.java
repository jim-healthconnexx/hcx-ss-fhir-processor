package com.hcx.fhir.processor.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * HDC-213: EnvironmentPostProcessor that fetches DB credentials from AWS Secrets Manager
 * and injects them as spring.datasource.* properties before Spring Boot's DataSource
 * auto-configuration runs.
 *
 * <p>Only activates when {@code aws.secrets.db-credentials-arn} is set in the environment
 * (e.g. application-qa.properties). Local/dev profiles without that property are unaffected.
 *
 * <p>The secret must be a JSON object with keys: username, password, host, port, dbname.
 * The dbname value must include JDBC parameters (e.g. {@code hcx?currentSchema=healthdata});
 * it is passed through verbatim so no schema suffix is appended here.
 */
public class DbSecretsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    // HDC-213: Cannot use @Slf4j here — Lombok annotations don't work on EnvironmentPostProcessor
    // because it runs before the Spring context (and thus Lombok-generated loggers) initializes.
    private static final Logger log = LoggerFactory.getLogger(DbSecretsEnvironmentPostProcessor.class);

    private static final String PROPERTY_SOURCE_NAME = "awsSecretsManagerDbCredentials";
    private static final String ARN_PROPERTY = "aws.secrets.db-credentials-arn";
    private static final String REGION_PROPERTY = "aws.region";
    private static final String DEFAULT_REGION = "us-east-1";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // HDC-213: This processor runs before LoggingApplicationListener applies logging.level.*
        // settings. Pair every diagnostic log call with System.err.println() so output is
        // guaranteed to appear in CloudWatch (ECS awslogs driver captures stderr).
        String[] activeProfiles = environment.getActiveProfiles();
        String secretArn = environment.getProperty(ARN_PROPERTY);
        String arnStatus = StringUtils.hasText(secretArn) ? "[set]" : "[blank/not-found]";

        System.err.println("HDC-213: DbSecretsEnvironmentPostProcessor started — activeProfiles="
                + Arrays.toString(activeProfiles) + " " + ARN_PROPERTY + "=" + arnStatus);
        log.info("HDC-213: DbSecretsEnvironmentPostProcessor started — activeProfiles={} {}={}",
                Arrays.toString(activeProfiles), ARN_PROPERTY, arnStatus);

        if (!StringUtils.hasText(secretArn)) {
            // HDC-213: No secret ARN configured — skip (local/dev profiles).
            String skipMsg = "HDC-213: WARN — " + ARN_PROPERTY
                    + " is blank or not found; skipping Secrets Manager DB credential injection."
                    + " Active profiles: " + Arrays.toString(activeProfiles)
                    + ". If this is qa or prod this is a misconfiguration.";
            System.err.println(skipMsg);
            log.warn("HDC-213: {} is blank or not found in environment; skipping Secrets Manager"
                    + " DB credential injection. Active profiles: {}",
                    ARN_PROPERTY, Arrays.toString(activeProfiles));
            return;
        }

        String region = environment.getProperty(REGION_PROPERTY, DEFAULT_REGION);
        log.info("HDC-213: Fetching DB credentials from Secrets Manager ARN {} in region {}", secretArn, region);

        try (SecretsManagerClient client = SecretsManagerClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build()) {

            GetSecretValueResponse response = client.getSecretValue(
                    GetSecretValueRequest.builder().secretId(secretArn).build());

            Map<String, String> secretMap = objectMapper.readValue(
                    response.secretString(), new TypeReference<Map<String, String>>() {});

            String host = requiredKey(secretMap, "host", secretArn);
            String port = requiredKey(secretMap, "port", secretArn);
            String dbname = requiredKey(secretMap, "dbname", secretArn);
            String username = requiredKey(secretMap, "username", secretArn);
            String password = requiredKey(secretMap, "password", secretArn);

            // HDC-213: Pass dbname verbatim — the secret's dbname value already carries any
            // required JDBC parameters (e.g. ?currentSchema=healthdata). Appending them here
            // would duplicate the parameter and produce a malformed URL.
            String jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s", host, port, dbname);

            Map<String, Object> props = new HashMap<>();
            props.put("spring.datasource.url", jdbcUrl);
            props.put("spring.datasource.username", username);
            props.put("spring.datasource.password", password);

            // HDC-213: Insert at the front so these values take precedence over any profile properties.
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, props));

            log.info("HDC-213: DB credentials injected — url={}, username={}, password=***", jdbcUrl, username);
            System.err.println("HDC-213: DB credentials injected successfully — url=" + jdbcUrl
                    + " username=" + username + " password=***");

        } catch (Exception e) {
            log.error("HDC-213: Failed to fetch DB credentials from Secrets Manager ARN {}: {}",
                    secretArn, e.getMessage(), e);
            throw new IllegalStateException(
                    "HDC-213: Cannot start application — DB credentials unavailable from Secrets Manager: " + secretArn, e);
        }
    }

    private String requiredKey(Map<String, String> map, String key, String arn) {
        String value = map.get(key);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    String.format("HDC-213: Secret at %s is missing required key '%s'", arn, key));
        }
        return value;
    }
}
