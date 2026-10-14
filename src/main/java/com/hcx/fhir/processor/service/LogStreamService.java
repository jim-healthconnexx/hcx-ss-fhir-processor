package com.hcx.fhir.processor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

// HDC-308: Resolves the CloudWatch log stream name for the current app run, for use in
// healthdata.panel_tracker.log_stream. Reviewed HDC-305 (hcx-ss-sftp-send) and reuses the
// same ECS Task Metadata Endpoint v4 approach here.
@Slf4j
@Service
@RequiredArgsConstructor
public class LogStreamService {

    private static final String METADATA_URI_ENV_VAR = "ECS_CONTAINER_METADATA_URI_V4";
    private static final String LOCAL_FALLBACK = "local";

    private final ObjectMapper objectMapper;

    // HDC-308: Resolved once per run and cached — the log stream can't change mid-task.
    private String cachedLogStream;

    /**
     * Returns the CloudWatch log stream name for the current ECS task run, resolved via the
     * ECS Task Metadata Endpoint v4 ({@code LogOptions.awslogs-stream}). Falls back to
     * {@code "local"} when the metadata endpoint env var isn't present (e.g. local dev) or
     * the lookup fails — this is never fatal to panel processing.
     */
    public String resolveLogStream() {
        if (cachedLogStream != null) {
            return cachedLogStream;
        }

        String metadataUri = System.getenv(METADATA_URI_ENV_VAR);
        if (metadataUri == null || metadataUri.isBlank()) {
            log.debug("HDC-308: {} not set — using local fallback for log_stream", METADATA_URI_ENV_VAR);
            cachedLogStream = LOCAL_FALLBACK;
            return cachedLogStream;
        }

        try {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(metadataUri))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode streamNode = root.path("LogOptions").path("awslogs-stream");
            if (streamNode.isMissingNode() || streamNode.isNull()) {
                log.warn("HDC-308: ECS metadata response missing LogOptions.awslogs-stream — using local fallback");
                cachedLogStream = LOCAL_FALLBACK;
            } else {
                cachedLogStream = streamNode.asText();
                log.debug("HDC-308: Resolved log_stream={}", cachedLogStream);
            }
        } catch (Exception e) {
            log.warn("HDC-308: Failed to resolve log_stream from ECS metadata endpoint — using local fallback", e);
            cachedLogStream = LOCAL_FALLBACK;
        }

        return cachedLogStream;
    }
}
