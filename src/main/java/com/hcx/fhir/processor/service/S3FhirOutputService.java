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

// HDC-175: Uploads assembled FHIR JSON to S3.
@Slf4j
@Service
@RequiredArgsConstructor
public class S3FhirOutputService {

    private final S3Client s3Client;
    private final AwsS3Properties s3Properties;

    // HDC-175: Saves the FHIR JSON to S3 using the panel-derived filename.
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

    // HDC-175: Builds the S3 filename: {dataSource without .txt}-{sentRequestFilename without .txt}-fhir.json
    String buildFilename(PanelRecord panel) {
        String dataSource = stripTxt(panel.dataSource());
        String sentRequest = stripTxt(panel.sentRequestFilename());
        return dataSource + "-" + sentRequest + "-fhir.json";
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
