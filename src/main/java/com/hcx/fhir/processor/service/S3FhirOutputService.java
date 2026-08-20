package com.hcx.fhir.processor.service;

import com.hcx.fhir.processor.config.AwsS3Properties;
import com.hcx.fhir.processor.model.PanelRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

// HDC-175: Uploads assembled FHIR JSON to S3.
// HDC-261: Added per-page timestamped upload; original method deprecated.
@Slf4j
@Service
@RequiredArgsConstructor
public class S3FhirOutputService {

    // HDC-261: Timestamp format for per-page FHIR filenames (millisecond precision avoids same-second collision).
    private static final DateTimeFormatter PAGE_TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'").withZone(ZoneOffset.UTC);

    private final S3Client s3Client;
    private final AwsS3Properties s3Properties;

    // HDC-261: Saves a single FHIR page to S3 with a timestamp-stamped filename.
    // Filename format: {dataSource}-{sentRequest}-fhir_{timestamp}.json
    public void saveToS3(String fhirJson, PanelRecord panel, String timestamp) {
        String filename = buildFilenameWithTimestamp(panel, timestamp);
        String key = buildKey(filename);
        String bucket = s3Properties.getFhirOutputBucket();

        log.debug("HDC-261: Uploading FHIR page panelId={} to s3://{}/{}", panel.panelId(), bucket, key);
        try {
            byte[] bytes = fhirJson.getBytes(StandardCharsets.UTF_8);
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType("application/json")
                            .build(),
                    RequestBody.fromBytes(bytes));
            log.debug("HDC-261: Uploaded FHIR page panelId={} to s3://{}/{}", panel.panelId(), bucket, key);
        } catch (Exception e) {
            log.error("HDC-261: Failed to upload FHIR page panelId={} to s3://{}/{}", panel.panelId(), bucket, key, e);
            throw new RuntimeException("HDC-261: Failed to upload FHIR page to S3", e);
        }
    }

    // HDC-175: Saves the FHIR JSON to S3 using the panel-derived filename.
    // HDC-261: Replaced by saveToS3(json, panel, timestamp) for per-page processing.
    @Deprecated
    public void saveToS3(String fhirJson, PanelRecord panel) {
        String filename = buildFilename(panel);
        String key = buildKey(filename);
        String bucket = s3Properties.getFhirOutputBucket();

        log.debug("HDC-175: Uploading FHIR JSON panelId={} to s3://{}/{}", panel.panelId(), bucket, key);
        try {
            byte[] bytes = fhirJson.getBytes(StandardCharsets.UTF_8);
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType("application/json")
                            .build(),
                    RequestBody.fromBytes(bytes));
            log.debug("HDC-175: Uploaded FHIR JSON panelId={} to s3://{}/{}", panel.panelId(), bucket, key);
        } catch (Exception e) {
            log.error("HDC-175: Failed to upload FHIR JSON panelId={} to s3://{}/{}", panel.panelId(), bucket, key, e);
            throw new RuntimeException("HDC-175: Failed to upload FHIR JSON to S3", e);
        }
    }

    // HDC-239: Uploads the CapabilityStatement JSON to S3 as capabilities_statement.json.
    public void saveCapabilitiesStatementToS3(String json) {
        String key = buildKey("capabilities_statement.json");
        String bucket = s3Properties.getFhirOutputBucket();

        log.debug("HDC-239: Uploading CapabilityStatement to s3://{}/{}", bucket, key);
        try {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType("application/json")
                            .build(),
                    RequestBody.fromBytes(bytes));
            log.info("HDC-239: Uploaded CapabilityStatement to s3://{}/{}", bucket, key);
        } catch (Exception e) {
            log.error("HDC-239: Failed to upload CapabilityStatement to s3://{}/{}", bucket, key, e);
            throw new RuntimeException("HDC-239: Failed to upload CapabilityStatement to S3", e);
        }
    }

    // HDC-175: Builds the S3 filename: {dataSource without .txt}-{sentRequestFilename without .txt}-fhir.json
    // HDC-261: Replaced by buildFilenameWithTimestamp() for per-page processing.
    @Deprecated
    String buildFilename(PanelRecord panel) {
        String dataSource = stripTxt(panel.dataSource());
        String sentRequest = stripTxt(panel.sentRequestFilename());
        return dataSource + "-" + sentRequest + "-fhir.json";
    }

    // HDC-261: Builds a timestamped S3 filename for a single FHIR page.
    // Format: {dataSource}-{sentRequest}-fhir_{timestamp}.json
    String buildFilenameWithTimestamp(PanelRecord panel, String timestamp) {
        String dataSource = stripTxt(panel.dataSource());
        String sentRequest = stripTxt(panel.sentRequestFilename());
        return dataSource + "-" + sentRequest + "-fhir_" + timestamp + ".json";
    }

    // HDC-261: Returns a millisecond-precision UTC timestamp string for page filenames.
    public String currentPageTimestamp() {
        return PAGE_TIMESTAMP_FMT.format(Instant.now());
    }

    private String buildKey(String filename) {
        String prefix = s3Properties.getFhirOutputKeyPrefix();
        if (prefix == null || prefix.isBlank()) {
            return filename;
        }
        return prefix.endsWith("/") ? prefix + filename : prefix + "/" + filename;
    }

    private String stripTxt(String value) {
        if (value == null) return "";
        return value.endsWith(".txt") ? value.substring(0, value.length() - 4) : value;
    }
}
